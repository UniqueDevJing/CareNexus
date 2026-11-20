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

/** 人工导诊队列的最小业务闭环，供管理员和护士端复用。 */
@Service
public class ManualReviewService {
    private final Db db;

    public ManualReviewService(Db db) { this.db = db; }

    public List<Map<String, Object>> queue() {
        List<Map<String, Object>> rows = new ArrayList<>(db.query(
                "SELECT s.id suggestion_id, s.session_id, s.patient_id, s.department_id, s.doctor_id, "
                        + "p.name patient_name, p.gender, p.age, p.past_history, p.allergic_history, "
                        + "dep.name department_name, doc.name doctor_name, s.confidence, s.red_flag, s.reason, s.create_time "
                        + "FROM triage_suggestion s LEFT JOIN patient_profile p ON p.id=s.patient_id "
                        + "LEFT JOIN department dep ON dep.id=s.department_id LEFT JOIN doctor doc ON doc.id=s.doctor_id "
                        + "WHERE s.status='PENDING' AND s.registered=0 ORDER BY s.id DESC LIMIT 50", Map.of()));
        for (Map<String, Object> row : rows) {
            Map<String, Object> message = db.queryOne(
                    "SELECT content FROM pre_triage_message WHERE session_id=:s AND role='user' "
                            + "ORDER BY id DESC LIMIT 1", Map.of("s", row.get("session_id")));
            row.put("last_user_message", message == null ? "" : message.getOrDefault("content", ""));
        }
        return rows;
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
            throw new BizException("该分诊建议已处理，无需重复操作");
        }
        String note = String.valueOf(body.getOrDefault("note", "人工导诊处理")).trim();
        if (note.length() > 220) note = note.substring(0, 220);
        String oldReason = String.valueOf(suggestion.getOrDefault("reason", ""));
        String reason = (oldReason + "（" + note + "）");
        if (reason.length() > 500) reason = reason.substring(0, 500);
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
            db.update("UPDATE triage_suggestion SET department_id=:dept, doctor_id=:doctor, status='CONFIRMED', reason=:reason WHERE id=:id",
                    Map.of("dept", dept, "doctor", doctor, "reason", reason, "id", suggestionId));
            if (suggestion.get("session_id") != null) {
                db.update("UPDATE pre_triage_session SET locked=1, status='LOCKED', summary=:summary WHERE id=:id",
                        Map.of("summary", "人工导诊已确认，可继续预约", "id", suggestion.get("session_id")));
            }
        } else if ("REJECT".equals(action)) {
            db.update("UPDATE triage_suggestion SET status='REJECTED', reason=:reason WHERE id=:id",
                    Map.of("reason", reason, "id", suggestionId));
        } else {
            throw new BizException("只支持 CONFIRM 或 REJECT");
        }
        db.insert("INSERT INTO operation_audit (operator, action, target, detail) VALUES (:operator,:action,:target,:detail)",
                Map.of("operator", principal == null ? "manual-review" : principal.username(),
                        "action", "MANUAL_TRIAGE_" + action, "target", "triage_suggestion:" + suggestionId,
                        "detail", reason));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestionId", suggestionId);
        result.put("status", "CONFIRM".equals(action) ? "CONFIRMED" : "REJECTED");
        return result;
    }
}
