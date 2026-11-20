package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import com.hospital.v2.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工导诊队列：只把异常与不确定情况交给护士，并支持“向患者补问”后重新入队。
 *
 * PENDING          护士待处理
 * NEED_MORE_INFO   已向患者发出关键追问，等待患者补充
 * CONFIRMED        已确认，可继续预约
 * REJECTED         已转急诊/人工医生或结束当前建议
 * SUPERSEDED       患者重新分诊后，旧建议不再生效
 */
@Service
public class ManualReviewService {
    private final Db db;

    public ManualReviewService(Db db) { this.db = db; }

    public List<Map<String, Object>> queue() {
        List<Map<String, Object>> rows = new ArrayList<>(db.query(
                "SELECT s.id suggestion_id, s.session_id, s.patient_id, s.department_id, s.doctor_id, "
                        + "p.name patient_name, p.gender, p.age, p.past_history, p.allergic_history, "
                        + "dep.name department_name, doc.name doctor_name, s.confidence, s.red_flag, s.reason, "
                        + "s.status, s.manual_question, s.review_note, s.reviewer, s.review_time, s.create_time "
                        + "FROM triage_suggestion s LEFT JOIN patient_profile p ON p.id=s.patient_id "
                        + "LEFT JOIN department dep ON dep.id=s.department_id LEFT JOIN doctor doc ON doc.id=s.doctor_id "
                        + "WHERE s.status IN ('PENDING','NEED_MORE_INFO') AND s.registered=0 "
                        + "ORDER BY CASE WHEN s.red_flag=1 THEN 0 WHEN s.status='PENDING' THEN 1 ELSE 2 END, "
                        + "s.id DESC LIMIT 50", Map.of()));
        for (Map<String, Object> row : rows) {
            Map<String, Object> message = db.queryOne(
                    "SELECT content FROM pre_triage_message WHERE session_id=:s AND role='user' "
                            + "ORDER BY id DESC LIMIT 1", Map.of("s", row.get("session_id")));
            row.put("last_user_message", message == null ? "" : message.getOrDefault("content", ""));
            row.put("queue_code", queueCode(row.get("suggestion_id")));
            row.put("actionable", "PENDING".equals(String.valueOf(row.get("status"))));
        }
        return rows;
    }

    /** 患者回复护士追问后，原建议回到护士待处理队列。 */
    public void reopenAfterPatientReply(long sessionId) {
        db.update("UPDATE triage_suggestion SET status='PENDING', manual_question=NULL "
                        + "WHERE session_id=:session AND status='NEED_MORE_INFO' AND registered=0",
                Map.of("session", sessionId));
    }

    @Transactional
    public Map<String, Object> resolve(Map<String, Object> body, AuthPrincipal principal) {
        long suggestionId = Long.parseLong(String.valueOf(body.get("suggestionId")));
        String action = String.valueOf(body.getOrDefault("action", "")).toUpperCase(java.util.Locale.ROOT);
        Map<String, Object> suggestion = db.queryOne(
                "SELECT id, session_id, department_id, doctor_id, red_flag, status, reason FROM triage_suggestion WHERE id=:id",
                Map.of("id", suggestionId));
        if (suggestion == null) throw new BizException("分诊建议不存在");
        if (!"PENDING".equals(String.valueOf(suggestion.get("status")))) {
            throw new BizException("该分诊建议当前不在护士待处理状态");
        }
        String note = text(body.get("note"));
        if (note.isBlank()) note = "人工导诊处理";
        if (note.length() > 220) note = note.substring(0, 220);
        String oldReason = text(suggestion.get("reason"));
        String reason = oldReason.isBlank() ? note : oldReason + "（" + note + "）";
        if (reason.length() > 500) reason = reason.substring(0, 500);
        String reviewer = principal == null ? "manual-review" : principal.username();

        if ("CONFIRM".equals(action)) {
            if (Integer.valueOf(String.valueOf(suggestion.getOrDefault("red_flag", 0))) == 1) {
                throw new BizException("命中风险规则的患者不能直接确认普通预约，请转急诊或人工医生处理");
            }
            long dept = body.get("departmentId") == null
                    ? ((Number) suggestion.get("department_id")).longValue()
                    : Long.parseLong(String.valueOf(body.get("departmentId")));
            long doctor = body.get("doctorId") == null
                    ? ((Number) suggestion.get("doctor_id")).longValue()
                    : Long.parseLong(String.valueOf(body.get("doctorId")));
            Map<String, Object> doctorRow = db.queryOne(
                    "SELECT id FROM doctor WHERE id=:doctor AND department_id=:dept",
                    Map.of("doctor", doctor, "dept", dept));
            if (doctorRow == null) throw new BizException("医生不属于所选科室");
            Map<String, Object> slot = db.queryOne(
                    "SELECT id FROM doctor_schedule WHERE doctor_id=:doctor AND status=1 AND remain_num>0 "
                            + "AND work_date>=CURRENT_DATE ORDER BY work_date, period LIMIT 1",
                    Map.of("doctor", doctor));
            if (slot == null) throw new BizException("当前医生暂无可用号源，请改派医生或联系人工服务");
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("dept", dept); update.put("doctor", doctor); update.put("reason", reason);
            update.put("reviewer", reviewer); update.put("id", suggestionId);
            int updated = db.update("UPDATE triage_suggestion SET department_id=:dept, doctor_id=:doctor, status='CONFIRMED', "
                            + "manual_question=NULL, review_note=:reason, reviewer=:reviewer, "
                            + "review_time=CURRENT_TIMESTAMP, reason=:reason WHERE id=:id AND status='PENDING'", update);
            if (updated == 0) throw new BizException("该分诊建议刚刚被其他护士处理，请刷新队列");
            if (suggestion.get("session_id") != null) {
                db.update("UPDATE pre_triage_session SET locked=1, status='LOCKED', summary=:summary WHERE id=:id",
                        Map.of("summary", "人工导诊已确认，可继续预约", "id", suggestion.get("session_id")));
            }
        } else if ("REQUEST_INFO".equals(action)) {
            if (Integer.valueOf(String.valueOf(suggestion.getOrDefault("red_flag", 0))) == 1) {
                throw new BizException("命中危急征象时不能等待患者补充，请转急诊或人工医生处理");
            }
            if (note.length() < 6) throw new BizException("请写清楚需要患者补充的关键问题");
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("question", note); update.put("reason", reason); update.put("reviewer", reviewer);
            update.put("id", suggestionId);
            int updated = db.update("UPDATE triage_suggestion SET status='NEED_MORE_INFO', manual_question=:question, "
                            + "review_note=:reason, reviewer=:reviewer, review_time=CURRENT_TIMESTAMP, reason=:reason "
                            + "WHERE id=:id AND status='PENDING'", update);
            if (updated == 0) throw new BizException("该分诊建议刚刚被其他护士处理，请刷新队列");
            if (suggestion.get("session_id") != null) {
                db.update("UPDATE pre_triage_session SET summary=:summary WHERE id=:id",
                        Map.of("summary", "护士需要补充关键信息：" + note, "id", suggestion.get("session_id")));
            }
        } else if ("REJECT".equals(action)) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("reason", reason); update.put("reviewer", reviewer); update.put("id", suggestionId);
            int updated = db.update("UPDATE triage_suggestion SET status='REJECTED', manual_question=NULL, review_note=:reason, "
                            + "reviewer=:reviewer, review_time=CURRENT_TIMESTAMP, reason=:reason "
                            + "WHERE id=:id AND status='PENDING'", update);
            if (updated == 0) throw new BizException("该分诊建议刚刚被其他护士处理，请刷新队列");
            if (suggestion.get("session_id") != null) {
                db.update("UPDATE pre_triage_session SET locked=1, status='LOCKED', summary=:summary WHERE id=:id",
                        Map.of("summary", "当前分诊建议已转人工处理，请联系门诊服务台", "id", suggestion.get("session_id")));
            }
        } else {
            throw new BizException("只支持 CONFIRM、REQUEST_INFO 或 REJECT");
        }

        db.insert("INSERT INTO operation_audit (operator, action, target, detail) VALUES (:operator,:action,:target,:detail)",
                Map.of("operator", reviewer,
                        "action", "MANUAL_TRIAGE_" + action, "target", "triage_suggestion:" + suggestionId,
                        "detail", reason));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestionId", suggestionId);
        result.put("queueCode", queueCode(suggestionId));
        result.put("status", "CONFIRM".equals(action) ? "CONFIRMED"
                : "REQUEST_INFO".equals(action) ? "NEED_MORE_INFO" : "REJECTED");
        result.put("nextAction", "CONFIRM".equals(action) ? "PATIENT_CAN_BOOK"
                : "REQUEST_INFO".equals(action) ? "WAIT_FOR_PATIENT" : "ESCALATE_OR_END");
        return result;
    }

    private String queueCode(Object id) {
        try { return "TRIAGE-" + String.format("%06d", Long.parseLong(String.valueOf(id))); }
        catch (Exception e) { return "TRIAGE-UNKNOWN"; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
