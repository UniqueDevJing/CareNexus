package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import com.hospital.v2.domain.TriageResult;
import com.hospital.v2.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 集成测试：在 H2 零依赖上下文中跑通 ReAct 分诊 + 危急硬拦截 + 挂号乐观锁。 */
@SpringBootTest
class HospitalV2FlowTest {

    @Autowired
    private PreTriageService preTriage;
    @Autowired
    private ReActTriageAgent agent;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private ManualReviewService manualReviews;
    @Autowired
    private Db db;

    @BeforeEach
    void keepFixtureSchedulesInTheFuture() {
        List<Map<String, Object>> schedules = db.query(
                "SELECT id FROM doctor_schedule WHERE doctor_id=6 ORDER BY id", Map.of());
        for (int i = 0; i < schedules.size(); i++) {
            db.update("UPDATE doctor_schedule SET work_date=:date WHERE id=:id",
                    Map.of("date", LocalDate.now().plusDays(7L + i), "id", schedules.get(i).get("id")));
        }
    }

    @Test
    void normal_triage_returns_valid_department() {
        long sid = preTriage.startSession(1L);
        TriageResult r = agent.triage(sid, 1L, "咳嗽一周，夜间加重");
        Map<String, Object> sug = r.suggestion();
        assertNotNull(sug);
        assertTrue(((Number) sug.get("department_id")).longValue() > 0, "应给出有效科室");
        assertNotNull(sug.get("doctor_id"));
        assertNotNull(sug.get("suggested_date"));
        assertEquals(0, ((Number) sug.get("red_flag")).intValue(), "普通症状不应触发危急");
    }

    @Test
    void critical_complaint_hard_routes_to_emergency() {
        long sid = preTriage.startSession(1L);
        TriageResult r = agent.triage(sid, 1L, "突发胸痛大汗");
        Map<String, Object> sug = r.suggestion();
        assertEquals(5L, ((Number) sug.get("department_id")).longValue(), "危急应硬转急诊科(id=5)");
        assertEquals(1, ((Number) sug.get("red_flag")).intValue());
    }

    @Test
    void duplicate_registration_rejected() {
        long sid = preTriage.startSession(2L);
        TriageResult r = agent.triage(sid, 2L, "头痛眩晕");
        long suggestionId = ((Number) r.suggestion().get("suggestion_id")).longValue();
        // 第一次挂号成功
        assertDoesNotThrow(() -> registration.register(suggestionId, 2L));
        // 同一建议重复挂号应抛业务异常（乐观锁 + 唯一约束）
        assertThrows(BizException.class, () -> registration.register(suggestionId, 2L));
    }

    @Test
    void nurse_question_waits_for_patient_then_reopens_queue() {
        long sid = preTriage.startSession(1L);
        TriageResult r = agent.triage(sid, 1L, "咳嗽一周，信息需要人工核对");
        long suggestionId = ((Number) r.suggestion().get("suggestion_id")).longValue();
        // 让测试明确覆盖人工队列状态，而不是依赖当前 Mock 模型是否返回低置信度。
        db.update("UPDATE triage_suggestion SET status='PENDING' WHERE id=:id", Map.of("id", suggestionId));

        Map<String, Object> asked = manualReviews.resolve(Map.of(
                "suggestionId", suggestionId,
                "action", "REQUEST_INFO",
                "note", "请补充症状开始时间以及是否伴随发热"),
                new AuthPrincipal(99L, "nurse-test", "nurse"));
        assertEquals("NEED_MORE_INFO", asked.get("status"));
        assertEquals("NEED_MORE_INFO", db.queryOne(
                "SELECT status FROM triage_suggestion WHERE id=:id", Map.of("id", suggestionId)).get("status"));

        preTriage.appendUser(sid, "从昨晚开始，没有发热");
        manualReviews.reopenAfterPatientReply(sid);
        assertEquals("PENDING", db.queryOne(
                "SELECT status FROM triage_suggestion WHERE id=:id", Map.of("id", suggestionId)).get("status"));
    }

    @Test
    void nurse_rejection_locks_session_and_ends_ambiguous_flow() {
        long sid = preTriage.startSession(1L);
        TriageResult r = agent.triage(sid, 1L, "信息无法确认，需要人工服务");
        long suggestionId = ((Number) r.suggestion().get("suggestion_id")).longValue();
        db.update("UPDATE triage_suggestion SET status='PENDING' WHERE id=:id", Map.of("id", suggestionId));

        Map<String, Object> rejected = manualReviews.resolve(Map.of(
                "suggestionId", suggestionId,
                "action", "REJECT",
                "note", "无法安全确认普通预约，转门诊人工服务"),
                new AuthPrincipal(99L, "nurse-test", "nurse"));
        assertEquals("REJECTED", rejected.get("status"));
        Map<String, Object> session = db.queryOne(
                "SELECT status, locked FROM pre_triage_session WHERE id=:id", Map.of("id", sid));
        assertEquals("LOCKED", session.get("status"));
        assertEquals(1, ((Number) session.get("locked")).intValue());
    }
}
