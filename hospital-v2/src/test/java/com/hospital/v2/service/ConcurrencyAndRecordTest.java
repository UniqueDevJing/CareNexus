package com.hospital.v2.service;

import com.hospital.v2.dao.Db;
import com.hospital.v2.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:concurrent_audit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class ConcurrencyAndRecordTest {
    @Autowired Db db;
    @Autowired RegistrationService registrations;
    @Autowired PreTriageService sessions;
    @Autowired ReActTriageAgent agent;
    @Autowired MedicalRecordService records;
    private long suggestion(long patient) {
        long id = ((Number) agent.triage(sessions.startSession(patient), patient, "普通不适").suggestion().get("suggestion_id")).longValue();
        db.update("UPDATE triage_suggestion SET doctor_id=1,department_id=1,status='CONFIRMED' WHERE id=:id", Map.of("id",id));
        return id;
    }
    @Test void last_slot_is_sold_once_under_concurrency() throws Exception {
        db.update("UPDATE doctor_schedule SET status=0 WHERE doctor_id=1", Map.of());
        long schedule = db.insert("INSERT INTO doctor_schedule(doctor_id,department_id,work_date,period,total_num,remain_num,status) VALUES(1,1,:date,'AM',1,1,1)", Map.of("date",LocalDate.now().plusDays(100)));
        long first=suggestion(1), second=suggestion(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long[] pair:new long[][]{{first,1},{second,2}}) results.add(pool.submit(() -> {
                gate.await();
                try { registrations.register(pair[0],pair[1]); return true; }
                catch(BizException soldOut) { return false; }
            }));
            gate.countDown();
            int successes=0; for(Future<Boolean> result:results) if(result.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
            assertEquals(0,((Number)db.queryOne("SELECT remain_num FROM doctor_schedule WHERE id=:id",Map.of("id",schedule)).get("remain_num")).intValue());
            assertEquals(1,((Number)db.queryOne("SELECT COUNT(*) c FROM registration WHERE schedule_id=:id",Map.of("id",schedule)).get("c")).intValue());
        } finally { pool.shutdownNow(); }
    }
    @Test void foreign_and_unconfirmed_suggestions_cannot_register() {
        long id=suggestion(1);
        assertThrows(BizException.class,()->registrations.register(id,2L));
        for(String status:List.of("PENDING","NEED_MORE_INFO","REJECTED","SUPERSEDED")) {
            db.update("UPDATE triage_suggestion SET status=:s WHERE id=:id",Map.of("s",status,"id",id));
            assertThrows(BizException.class,()->registrations.register(id,1L));
        }
    }
    @Test void record_is_unique_and_submission_locks_both_record_and_visit() {
        long registration=db.insert("INSERT INTO registration(patient_id,department_id,doctor_id,schedule_id,work_date,period,status,payment_status) VALUES(1,1,1,1,:date,'PM','PAID','PAID')",Map.of("date",LocalDate.now().plusDays(101)));
        Map<String,Object> body=new HashMap<>(Map.of("registration_id",registration,"patient_id",1,"doctor_id",1,"chief_complaint","测试"));
        long id=records.saveDraft(body);
        assertThrows(BizException.class,()->records.saveDraft(body));
        records.submit(id,registration);
        assertEquals("SUBMITTED",records.detail(id).get("status"));
        assertEquals("DONE",db.queryOne("SELECT status FROM registration WHERE id=:id",Map.of("id",registration)).get("status"));
        body.put("id",id);
        assertThrows(BizException.class,()->records.saveDraft(body));
        assertThrows(BizException.class,()->records.submit(id,registration));
    }
    @Test void supplemental_complaint_is_persisted_without_duplicate_history() {
        long session=sessions.startSession(1L);
        sessions.appendUser(session,"咳嗽一周");
        assertEquals("咳嗽一周，没有发热", sessions.triageComplaint(session,"没有发热"));
        assertEquals("咳嗽一周，没有发热", sessions.triageComplaint(session,"咳嗽一周，没有发热"));
        assertEquals(2, sessions.history(session).size());
    }
    @Test void unavailable_slots_do_not_reduce_clinical_confidence() {
        var before=agent.triage(sessions.startSession(1L),1L,"普通不适").suggestion();
        var schedules=db.query("SELECT id,status FROM doctor_schedule",Map.of());
        try {
            db.update("UPDATE doctor_schedule SET status=0",Map.of());
            var after=agent.triage(sessions.startSession(1L),1L,"普通不适").suggestion();
            assertEquals(before.get("confidence"),after.get("confidence"));
            assertEquals(before.get("needs_confirmation"),after.get("needs_confirmation"));
            assertEquals(false,after.get("can_register"));
            assertTrue(String.valueOf(after.get("reason")).contains("暂无可约号源"));
        } finally {
            for(var schedule:schedules) db.update("UPDATE doctor_schedule SET status=:status WHERE id=:id",schedule);
        }
    }
}
