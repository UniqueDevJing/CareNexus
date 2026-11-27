package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import com.hospital.v2.domain.TriageResult;
import com.hospital.v2.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
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
    private PaymentService payments;
    @Autowired
    private ClinicalOrderService orders;
    @Autowired
    private KnowledgeService knowledge;
    @Autowired
    private DashboardService dashboard;
    @Autowired
    private CareEpisodeService episodes;
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
    void dashboard_exposes_patient_effort_and_agent_metrics() {
        Map<String, Object> overview = dashboard.overview();
        assertTrue(overview.containsKey("avgConversationTurns"));
        assertTrue(overview.containsKey("manualInterventionRate"));
        assertTrue(overview.containsKey("avgAgentLatencyMs"));
    }

    @Test
    void supplemental_answer_preserves_persisted_risk_context() {
        long sid = preTriage.startSession(1L);
        preTriage.appendUser(sid, "突发胸痛大汗");
        preTriage.appendUser(sid, "没有发热");
        assertTrue(preTriage.memorySummary(sid).contains("突发胸痛大汗"));
        assertTrue(preTriage.memorySummary(sid).contains("没有发热"));
        String complaint = preTriage.triageComplaint(sid, "没有发热");
        assertEquals("突发胸痛大汗，没有发热", complaint);
        assertEquals(1, agent.triage(sid, 1L, complaint).suggestion().get("red_flag"));
        assertEquals("突发胸痛大汗，没有发热，无过敏史", preTriage.triageComplaint(sid, "无过敏史"));
    }

    @Test
    void triage_does_not_start_until_duration_safety_and_visit_goal_are_known() {
        long sid = preTriage.startSession(1L);
        assertFalse(preTriage.assessReadiness(sid, "胸闷").ready());
        preTriage.appendUser(sid, "胸闷");
        preTriage.appendUser(sid, "三天");
        assertFalse(preTriage.assessReadiness(sid, null).ready());
        preTriage.appendUser(sid, "没有发热和呼吸困难，想预约门诊");
        assertTrue(preTriage.assessReadiness(sid, null).ready());
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
    void care_episode_links_intake_triage_slots_and_is_idempotent() {
        Map<String, Object> opened = episodes.open(1L);
        long episodeId = ((Number) opened.get("id")).longValue();
        long sessionId = preTriage.startSession(1L, episodeId);
        episodes.attachSession(episodeId, 1L, sessionId);

        TriageResult result = agent.triage(sessionId, 1L, "咳嗽一周，夜间加重");
        long suggestionId = ((Number) result.suggestion().get("suggestion_id")).longValue();
        episodes.onTriage(sessionId, false, false, "症状与科室匹配");
        assertEquals(CareEpisodeService.TRIAGED, db.queryOne(
                "SELECT status FROM care_episode WHERE id=:id", Map.of("id", episodeId)).get("status"));

        // 同一回调重复到达时不得重复创建患者任务或事件。
        episodes.onTriage(sessionId, false, false, "症状与科室匹配");
        assertEquals(1, ((Number) db.queryOne(
                "SELECT COUNT(*) total FROM agent_task WHERE episode_id=:id AND task_type='SELECT_SLOT' AND status='OPEN'",
                Map.of("id", episodeId)).get("total")).intValue());

        episodes.onSlotsLoaded(suggestionId, 1L, true);
        assertEquals(CareEpisodeService.SLOT_PROPOSED, db.queryOne(
                "SELECT status FROM care_episode WHERE id=:id", Map.of("id", episodeId)).get("status"));
        episodes.onSlotsLoaded(suggestionId, 1L, true);
        assertEquals(1, ((Number) db.queryOne(
                "SELECT COUNT(*) total FROM agent_task WHERE episode_id=:id AND task_type='SELECT_SLOT' AND status='OPEN'",
                Map.of("id", episodeId)).get("total")).intValue());
        assertTrue(episodes.timeline(episodeId, 1L).stream().anyMatch(row ->
                "TRIAGE_COMPLETED".equals(row.get("event_type"))));
    }

    @Test
    void care_episode_rejects_illegal_direct_transition() {
        Map<String, Object> opened = episodes.open(2L);
        long episodeId = ((Number) opened.get("id")).longValue();
        assertThrows(BizException.class, () -> episodes.advance(episodeId, CareEpisodeService.COMPLETED,
                "TEST_INVALID", "SYSTEM", null, "invalid-transition-" + episodeId, "非法跳转", null));
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
    void negated_red_flag_keywords_do_not_route_to_emergency() {
        long sid = preTriage.startSession(1L);
        TriageResult r = agent.triage(sid, 1L, "咳嗽三天，没有发热和呼吸困难，想预约门诊");
        assertEquals(0, ((Number) r.suggestion().get("red_flag")).intValue());
        assertNotEquals("EMERGENCY", r.suggestion().get("availability_status"));
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

    @Test
    void evidence_retrieval_exposes_source_and_blocks_unreviewed_uploads() {
        Map<String, Object> asthma = knowledge.listDocs(1L).stream()
                .filter(row -> "支气管哮喘诊疗要点".equals(row.get("title")))
                .findFirst().orElseThrow();
        assertEquals("SOURCE_VERIFIED", asthma.get("review_status"));
        assertEquals("OFFICIAL_GUIDELINE", asthma.get("authority_level"));
        assertTrue(String.valueOf(asthma.get("source_url")).startsWith("https://"));

        long uploadId = knowledge.addDoc(1L, "未核验测试资料", "这是一段未登记来源的测试内容");
        assertEquals("PENDING_REVIEW", db.queryOne("SELECT review_status FROM knowledge_doc WHERE id=:id",
                Map.of("id", uploadId)).get("review_status"));

        List<Map<String, Object>> hits = knowledge.retrieve("哮喘如何评估和管理", 1L, 10);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().allMatch(hit -> "SOURCE_VERIFIED".equals(hit.get("review_status"))
                && String.valueOf(hit.get("source_url")).startsWith("https://")
                && hit.get("source_notice") != null));
        assertTrue(hits.stream().noneMatch(hit -> uploadId == ((Number) hit.get("doc_id")).longValue()));
    }

    @Test
    void balance_payment_is_atomic_and_refundable() {
        db.update("UPDATE patient_profile SET balance=:balance WHERE id=:id",
                Map.of("balance", new BigDecimal("100.00"), "id", 1L));
        long billId = payments.createRegistrationBill(90001L, 1L, new BigDecimal("12.50"));

        payments.payBill(billId, 1L, "BALANCE", null);
        assertEquals("PAID", db.queryOne("SELECT status FROM bill WHERE id=:id", Map.of("id", billId)).get("status"));
        assertEquals(new BigDecimal("87.50"), db.queryOne(
                "SELECT balance FROM patient_profile WHERE id=1", Map.of()).get("balance"));
        assertThrows(BizException.class, () -> payments.payBill(billId, 1L, "BALANCE", null));

        payments.refundBill(billId, "测试退费");
        assertEquals("REFUNDED", db.queryOne("SELECT status FROM bill WHERE id=:id", Map.of("id", billId)).get("status"));
        assertEquals(new BigDecimal("100.00"), db.queryOne(
                "SELECT balance FROM patient_profile WHERE id=1", Map.of()).get("balance"));
    }

    @Test
    void paid_clinical_orders_reach_pharmacy_and_are_dispensed_once() {
        db.update("UPDATE patient_profile SET balance=100.00 WHERE id=1", Map.of());
        LocalDate date = LocalDate.now().plusDays(40);
        long registrationId = db.insert("INSERT INTO registration(suggestion_id,patient_id,department_id,doctor_id,schedule_id,work_date,period,status,payment_status) "
                + "VALUES(NULL,1,1,1,1,:date,'AM','PAID','PAID')", Map.of("date", date));
        Map<String, Object> exam = orders.createExam(Map.of(
                "registrationId", registrationId, "patientId", 1L, "itemName", "血常规", "itemCode", "CBC", "fee", "5.00"), 1L);
        long examBill = ((Number) exam.get("bill_id")).longValue();
        payments.payBill(examBill, 1L, "BALANCE", null);
        assertEquals("PAID", db.queryOne("SELECT status FROM bill WHERE id=:id", Map.of("id", examBill)).get("status"));

        Map<String, Object> prescription = orders.createPrescription(Map.of(
                "registrationId", registrationId, "patientId", 1L,
                "items", List.of(Map.of("drugName", "示例药品", "dosage", "1片", "frequency", "每日一次",
                        "days", 3, "quantity", "2.00", "unitPrice", "2.00"))), 1L);
        long prescriptionId = ((Number) prescription.get("id")).longValue();
        long prescriptionBill = ((Number) prescription.get("bill_id")).longValue();
        assertTrue(orders.pharmacyQueue().stream().noneMatch(row -> ((Number) row.get("prescription_id")).longValue() == prescriptionId));
        assertThrows(BizException.class, () -> orders.dispense(prescriptionId, 5L));
        payments.payBill(prescriptionBill, 1L, "BALANCE", null);
        assertTrue(orders.pharmacyQueue().stream().anyMatch(row -> ((Number) row.get("prescription_id")).longValue() == prescriptionId));
        orders.dispense(prescriptionId, 5L);
        assertEquals("DISPENSED", db.queryOne("SELECT status FROM prescription WHERE id=:id", Map.of("id", prescriptionId)).get("status"));
        assertThrows(BizException.class, () -> orders.dispense(prescriptionId, 5L));
    }
}
