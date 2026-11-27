package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import com.hospital.v2.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊就医任务的唯一状态边界。
 * 页面不再通过多个模块猜测进度；所有关键动作都由本服务写入事件并生成下一步任务。
 */
@Service
public class CareEpisodeService {
    public static final String INTAKE = "INTAKE";
    public static final String TRIAGED = "TRIAGED";
    public static final String SLOT_PROPOSED = "SLOT_PROPOSED";
    public static final String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    public static final String BOOKED = "BOOKED";
    public static final String CHECKED_IN = "CHECKED_IN";
    public static final String WAITING_DOCTOR = "WAITING_DOCTOR";
    public static final String IN_CONSULTATION = "IN_CONSULTATION";
    public static final String ORDERS_PENDING = "ORDERS_PENDING";
    public static final String WAITING_RESULTS = "WAITING_RESULTS";
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String COMPLETED = "COMPLETED";
    public static final String FOLLOW_UP = "FOLLOW_UP";
    public static final String EMERGENCY_GUIDANCE = "EMERGENCY_GUIDANCE";

    private final Db db;

    public CareEpisodeService(Db db) {
        this.db = db;
    }

    @Transactional
    public Map<String, Object> open(long patientId) {
        if (patientId <= 0) throw new BizException("患者身份无效，无法开始就诊任务");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("patient", patientId);
        values.put("action", "COLLECT_SYMPTOMS");
        long episodeId = db.insert("INSERT INTO care_episode (patient_id,status,current_action) "
                + "VALUES (:patient,'INTAKE',:action)", values);
        appendEvent(episodeId, null, INTAKE, "EPISODE_OPENED", "PATIENT", patientId,
                "episode-opened", "患者开始本次就诊任务");
        createTask(episodeId, "COLLECT_SYMPTOMS", "OPEN", "NORMAL", "补充症状和就诊目标",
                "告诉我最困扰你的症状、持续时间，以及是否想预约门诊。", "PATIENT");
        return currentForPatient(episodeId, patientId);
    }

    public Map<String, Object> currentForPatient(long episodeId, long patientId) {
        Map<String, Object> episode = db.queryOne("SELECT * FROM care_episode WHERE id=:id AND patient_id=:patient",
                Map.of("id", episodeId, "patient", patientId));
        if (episode == null) throw new BizException("就诊任务不存在或无权访问");
        Map<String, Object> out = new LinkedHashMap<>(episode);
        out.put("next_tasks", openTasks(episodeId));
        out.put("timeline", timeline(episodeId, patientId));
        return out;
    }

    public Map<String, Object> latestForPatient(long patientId) {
        Map<String, Object> episode = db.queryOne("SELECT * FROM care_episode WHERE patient_id=:patient "
                        + "AND status NOT IN ('COMPLETED','CANCELLED') ORDER BY update_time DESC, id DESC LIMIT 1",
                Map.of("patient", patientId));
        if (episode == null) return null;
        return currentForPatient(((Number) episode.get("id")).longValue(), patientId);
    }

    public List<Map<String, Object>> timeline(long episodeId, long patientId) {
        assertOwner(episodeId, patientId);
        return db.query("SELECT id,event_type,from_status,to_status,actor_role,actor_id,payload_json,create_time "
                + "FROM care_episode_event WHERE episode_id=:episode ORDER BY id", Map.of("episode", episodeId));
    }

    public List<Map<String, Object>> openTasks(long episodeId) {
        return db.query("SELECT id,task_type,status,priority,title,detail,assigned_role,due_time,create_time "
                + "FROM agent_task WHERE episode_id=:episode AND status='OPEN' ORDER BY "
                + "CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END, id", Map.of("episode", episodeId));
    }

    /** 医生只看到分配给自己或尚未认领的临床接管任务。 */
    public List<Map<String, Object>> doctorTasks(long doctorId) {
        return db.query("SELECT t.id task_id,t.episode_id,s.id suggestion_id,t.task_type,t.status,t.priority,t.title,t.detail,"
                + "t.assigned_id,t.create_time,e.status episode_status,e.patient_id,p.name patient_name,"
                + "s.red_flag,s.reason,s.confidence FROM agent_task t "
                + "JOIN care_episode e ON e.id=t.episode_id LEFT JOIN patient_profile p ON p.id=e.patient_id "
                + "LEFT JOIN pre_triage_session ps ON ps.episode_id=e.id "
                + "LEFT JOIN triage_suggestion s ON s.session_id=ps.id AND s.id=(SELECT MAX(s2.id) FROM triage_suggestion s2 WHERE s2.session_id=ps.id) "
                + "WHERE t.assigned_role='DOCTOR' AND t.status IN ('OPEN','IN_PROGRESS') "
                + "AND (t.assigned_id IS NULL OR t.assigned_id=:doctor) ORDER BY "
                + "CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,t.id DESC",
                Map.of("doctor", doctorId));
    }

    public void completeClinicalTask(long suggestionId) {
        db.update("UPDATE agent_task SET status='DONE',complete_time=CURRENT_TIMESTAMP WHERE task_type='CLINICAL_REVIEW' "
                + "AND status IN ('OPEN','IN_PROGRESS') AND episode_id=(SELECT ps.episode_id FROM triage_suggestion s "
                + "JOIN pre_triage_session ps ON ps.id=s.session_id WHERE s.id=:suggestion)",
                Map.of("suggestion", suggestionId));
    }

    public void markReviewWaitingForPatient(long sessionId) {
        updateReviewTask(sessionId, "WAITING_PATIENT");
    }

    public void reopenReviewAfterPatientReply(long sessionId) {
        updateReviewTask(sessionId, "OPEN");
    }

    private void updateReviewTask(long sessionId, String status) {
        db.update("UPDATE agent_task SET status=:status WHERE task_type='CLINICAL_REVIEW' "
                + "AND episode_id=(SELECT episode_id FROM pre_triage_session WHERE id=:session)",
                Map.of("status", status, "session", sessionId));
    }

    @Transactional
    public void attachSession(long episodeId, long patientId, long sessionId) {
        assertOwner(episodeId, patientId);
        db.update("UPDATE pre_triage_session SET episode_id=:episode WHERE id=:session AND patient_id=:patient",
                Map.of("episode", episodeId, "session", sessionId, "patient", patientId));
        appendEvent(episodeId, INTAKE, INTAKE, "SESSION_ATTACHED", "SYSTEM", null,
                "session:" + sessionId, "已关联预问诊会话");
    }

    @Transactional
    public void onTriage(long sessionId, boolean redFlag, boolean needsReview, String reason) {
        Map<String, Object> row = db.queryOne("SELECT episode_id,patient_id FROM pre_triage_session WHERE id=:id",
                Map.of("id", sessionId));
        if (row == null || row.get("episode_id") == null) return;
        long episodeId = ((Number) row.get("episode_id")).longValue();
        long patientId = ((Number) row.get("patient_id")).longValue();
        if (redFlag) {
            if (!transition(episodeId, patientId, EMERGENCY_GUIDANCE, "RISK_DETECTED", "SYSTEM", null,
                    "triage-risk:" + sessionId, reason)) return;
            closeOpenTasks(episodeId);
            createTask(episodeId, "EMERGENCY_ESCALATION", "OPEN", "URGENT", "优先联系急诊或人工医生",
                    reason, "CLINICAL");
            return;
        }
        if (needsReview) {
            if (!transition(episodeId, patientId, NEEDS_REVIEW, "CLINICAL_HANDOFF", "SYSTEM", null,
                    "triage-review:" + sessionId, reason)) return;
            closeOpenTasks(episodeId);
            Map<String, Object> suggestion = db.queryOne("SELECT doctor_id FROM triage_suggestion WHERE session_id=:session "
                    + "ORDER BY id DESC LIMIT 1", Map.of("session", sessionId));
            Long doctorId = suggestion == null || suggestion.get("doctor_id") == null
                    ? null : ((Number) suggestion.get("doctor_id")).longValue();
            createTask(episodeId, "CLINICAL_REVIEW", "OPEN", "HIGH", "临床人员确认就诊方向",
                    reason, "DOCTOR", doctorId);
            return;
        }
        if (!transition(episodeId, patientId, TRIAGED, "TRIAGE_COMPLETED", "SYSTEM", null,
                "triage-complete:" + sessionId, reason)) return;
        closeOpenTasks(episodeId);
        createTask(episodeId, "SELECT_SLOT", "OPEN", "NORMAL", "选择就诊时间",
                "系统已经匹配到就诊方向，请选择可用号源。", "PATIENT");
    }

    @Transactional
    public void onBooked(long suggestionId, long patientId, long registrationId) {
        Map<String, Object> row = db.queryOne("SELECT s.session_id, p.episode_id FROM triage_suggestion s "
                + "JOIN pre_triage_session p ON p.id=s.session_id WHERE s.id=:suggestion AND s.patient_id=:patient",
                Map.of("suggestion", suggestionId, "patient", patientId));
        if (row == null || row.get("episode_id") == null) return;
        long episodeId = ((Number) row.get("episode_id")).longValue();
        if (!transition(episodeId, patientId, BOOKED, "APPOINTMENT_BOOKED", "PATIENT", patientId,
                "registration:" + registrationId, "预约已确认")) return;
        closeOpenTasks(episodeId);
        createTask(episodeId, "ARRIVAL_CHECKIN", "OPEN", "NORMAL", "到院后完成签到",
                "请按预约时间提前到达并完成签到。", "PATIENT");
    }

    @Transactional
    public void onSlotsLoaded(long suggestionId, long patientId, boolean hasSlots) {
        Map<String, Object> row = db.queryOne("SELECT p.episode_id FROM triage_suggestion s "
                + "JOIN pre_triage_session p ON p.id=s.session_id WHERE s.id=:suggestion AND s.patient_id=:patient",
                Map.of("suggestion", suggestionId, "patient", patientId));
        if (row == null || row.get("episode_id") == null) return;
        long episodeId = ((Number) row.get("episode_id")).longValue();
        if (!transition(episodeId, patientId, hasSlots ? SLOT_PROPOSED : WAITING_CONFIRMATION,
                hasSlots ? "SLOTS_MATCHED" : "SLOTS_UNAVAILABLE", "SYSTEM", null,
                "slots:" + suggestionId + ":" + hasSlots, hasSlots ? "已找到可预约号源" : "当前暂无可用号源")) return;
        closeOpenTasks(episodeId);
        createTask(episodeId, "SELECT_SLOT", "OPEN", hasSlots ? "NORMAL" : "HIGH",
                hasSlots ? "选择就诊时间" : "等待号源或调整安排",
                hasSlots ? "请选择一个可用时间完成预约。" : "当前没有可用号源，稍后可重试或调整医生。", "PATIENT");
    }

    @Transactional
    public Map<String, Object> advance(long episodeId, String targetStatus, String eventType,
                                       String actorRole, Long actorId, String idempotencyKey, String detail,
                                       AuthPrincipal principal) {
        Map<String, Object> episode = db.queryOne("SELECT patient_id,status FROM care_episode WHERE id=:id FOR UPDATE",
                Map.of("id", episodeId));
        if (episode == null) throw new BizException("就诊任务不存在");
        if (principal != null && "patient".equals(principal.role())) {
            Map<String, Object> patient = db.queryOne("SELECT id FROM patient_profile WHERE user_id=:user",
                    Map.of("user", principal.userId()));
            if (patient == null || !String.valueOf(episode.get("patient_id")).equals(String.valueOf(patient.get("id")))) {
                throw new BizException("无权推进其他患者的就诊任务");
            }
        }
        long patientId = ((Number) episode.get("patient_id")).longValue();
        transition(episodeId, patientId, targetStatus, eventType, actorRole, actorId, idempotencyKey, detail);
        return currentForPatient(episodeId, patientId);
    }

    private boolean transition(long episodeId, long patientId, String toStatus, String eventType,
                            String actorRole, Long actorId, String idempotencyKey, String detail) {
        Map<String, Object> current = db.queryOne("SELECT status FROM care_episode WHERE id=:id AND patient_id=:patient",
                Map.of("id", episodeId, "patient", patientId));
        if (current == null) throw new BizException("就诊任务不存在或无权访问");
        String from = String.valueOf(current.get("status"));
        if (from.equals(toStatus)) return false;
        if (!allowed(from, toStatus)) throw new BizException("不允许从 " + from + " 直接进入 " + toStatus);
        int updated = db.update("UPDATE care_episode SET status=:to,current_action=:action,summary=:summary,"
                        + "version=version+1,update_time=CURRENT_TIMESTAMP WHERE id=:id AND patient_id=:patient AND status=:from",
                Map.of("to", toStatus, "action", nextAction(toStatus), "summary", detail == null ? "" : clip(detail),
                        "id", episodeId, "patient", patientId, "from", from));
        if (updated == 0) throw new BizException("就诊任务状态已被其他操作更新，请刷新后重试");
        appendEvent(episodeId, from, toStatus, eventType, actorRole, actorId, idempotencyKey, detail);
        return true;
    }

    private boolean allowed(String from, String to) {
        if (EMERGENCY_GUIDANCE.equals(to)) return !COMPLETED.equals(from);
        return switch (from) {
            case INTAKE -> TRIAGED.equals(to) || NEEDS_REVIEW.equals(to);
            case TRIAGED -> SLOT_PROPOSED.equals(to) || WAITING_CONFIRMATION.equals(to) || BOOKED.equals(to);
            case SLOT_PROPOSED, WAITING_CONFIRMATION -> BOOKED.equals(to) || NEEDS_REVIEW.equals(to)
                    || SLOT_PROPOSED.equals(to) || WAITING_CONFIRMATION.equals(to);
            case BOOKED -> CHECKED_IN.equals(to) || WAITING_DOCTOR.equals(to);
            case CHECKED_IN, WAITING_DOCTOR -> IN_CONSULTATION.equals(to);
            case IN_CONSULTATION -> ORDERS_PENDING.equals(to) || COMPLETED.equals(to);
            case ORDERS_PENDING -> WAITING_RESULTS.equals(to) || COMPLETED.equals(to);
            case WAITING_RESULTS -> NEEDS_REVIEW.equals(to) || COMPLETED.equals(to);
            case NEEDS_REVIEW -> TRIAGED.equals(to) || IN_CONSULTATION.equals(to) || COMPLETED.equals(to);
            case COMPLETED -> FOLLOW_UP.equals(to);
            default -> false;
        };
    }

    private String nextAction(String status) {
        return switch (status) {
            case INTAKE -> "COLLECT_SYMPTOMS";
            case TRIAGED, SLOT_PROPOSED, WAITING_CONFIRMATION -> "SELECT_SLOT";
            case BOOKED -> "ARRIVAL_CHECKIN";
            case CHECKED_IN, WAITING_DOCTOR -> "WAIT_FOR_DOCTOR";
            case IN_CONSULTATION -> "DOCTOR_REVIEW";
            case ORDERS_PENDING -> "COMPLETE_ORDERS";
            case WAITING_RESULTS -> "WAIT_FOR_RESULTS";
            case NEEDS_REVIEW -> "CLINICAL_REVIEW";
            case COMPLETED -> "FOLLOW_UP";
            case FOLLOW_UP -> "FOLLOW_UP";
            case EMERGENCY_GUIDANCE -> "CONTACT_EMERGENCY";
            default -> "CONTACT_CLINICAL_TEAM";
        };
    }

    private void appendEvent(long episodeId, String from, String to, String type, String role, Long actor,
                             String idempotencyKey, String detail) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("detail", clip(detail));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("episode", episodeId); values.put("type", type); values.put("from", from); values.put("to", to);
        values.put("role", role == null ? "SYSTEM" : role); values.put("actor", actor);
        values.put("payload", com.hospital.v2.common.JsonUtils.toJson(p)); values.put("key", idempotencyKey);
        try {
            db.insert("INSERT INTO care_episode_event (episode_id,event_type,from_status,to_status,actor_role,actor_id,payload_json,idempotency_key) "
                    + "VALUES (:episode,:type,:from,:to,:role,:actor,:payload,:key)", values);
        } catch (RuntimeException duplicate) {
            if (idempotencyKey == null || !String.valueOf(duplicate.getMessage()).toLowerCase().contains("unique")) throw duplicate;
        }
    }

    private void createTask(long episodeId, String type, String status, String priority, String title,
                            String detail, String assignedRole) {
        createTask(episodeId, type, status, priority, title, detail, assignedRole, null);
    }

    private void createTask(long episodeId, String type, String status, String priority, String title,
                            String detail, String assignedRole, Long assignedId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("episode", episodeId); values.put("type", type); values.put("status", status);
        values.put("priority", priority); values.put("title", title); values.put("detail", clip(detail));
        values.put("role", assignedRole); values.put("assigned", assignedId);
        db.insert("INSERT INTO agent_task (episode_id,task_type,status,priority,title,detail,assigned_role,assigned_id) "
                + "VALUES (:episode,:type,:status,:priority,:title,:detail,:role,:assigned)", values);
    }

    private void closeOpenTasks(long episodeId) {
        db.update("UPDATE agent_task SET status='CANCELLED',complete_time=CURRENT_TIMESTAMP "
                + "WHERE episode_id=:episode AND status='OPEN'", Map.of("episode", episodeId));
    }

    private void assertOwner(long episodeId, long patientId) {
        Map<String, Object> row = db.queryOne("SELECT id FROM care_episode WHERE id=:id AND patient_id=:patient",
                Map.of("id", episodeId, "patient", patientId));
        if (row == null) throw new BizException("就诊任务不存在或无权访问");
    }

    private String clip(String text) {
        if (text == null) return "";
        String normalized = text.trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }
}
