package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医嘱业务：医生开检查/处方，检查结果与发药由工作人员回填。
 * 每张收费医嘱只生成一张幂等账单，未支付处方不会进入药房发药队列。
 */
@Service
public class ClinicalOrderService {
    private final Db db;
    private final PaymentService payments;

    public ClinicalOrderService(Db db, PaymentService payments) {
        this.db = db;
        this.payments = payments;
    }

    @Transactional
    public Map<String, Object> createExam(Map<String, Object> body, long doctorId) {
        long registrationId = requiredId(body, "registrationId");
        long patientId = requiredId(body, "patientId");
        assertDoctorRegistration(registrationId, patientId, doctorId);
        String name = requiredText(body, "itemName", 128);
        BigDecimal fee = amount(body.get("fee"));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("registration", registrationId); p.put("patient", patientId); p.put("doctor", doctorId);
        p.put("code", text(body.get("itemCode"))); p.put("name", name); p.put("fee", fee);
        long id = db.insert("INSERT INTO exam_order(registration_id,patient_id,doctor_id,item_code,item_name,fee,status) "
                + "VALUES(:registration,:patient,:doctor,:code,:name,:fee,'ORDERED')", p);
        long billId = payments.createExaminationBill(id, patientId, fee);
        return examDetail(id, patientId, billId);
    }

    @Transactional
    public Map<String, Object> createPrescription(Map<String, Object> body, long doctorId) {
        long registrationId = requiredId(body, "registrationId");
        long patientId = requiredId(body, "patientId");
        assertDoctorRegistration(registrationId, patientId, doctorId);
        Object rawItems = body.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty() || items.size() > 20) {
            throw new BizException("处方至少需要一项药品，最多 20 项");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> item)) throw new BizException("处方药品格式错误");
            String drugName = requiredText(item.get("drugName"), 128);
            String dosage = requiredText(item.get("dosage"), 64);
            String frequency = requiredText(item.get("frequency"), 64);
            int days = positiveInt(item.get("days"), "用药天数");
            BigDecimal quantity = positiveAmount(item.get("quantity"), "药品数量");
            BigDecimal unitPrice = nonNegativeAmount(item.get("unitPrice"), "药品单价");
            BigDecimal amount = unitPrice.multiply(quantity).setScale(2, java.math.RoundingMode.HALF_UP);
            total = total.add(amount);
            normalized.add(Map.of("code", text(item.get("drugCode")), "name", drugName, "dosage", dosage,
                    "frequency", frequency, "days", days, "quantity", quantity, "unitPrice", unitPrice, "amount", amount));
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("registration", registrationId); p.put("patient", patientId); p.put("doctor", doctorId);
        p.put("total", total); p.put("note", text(body.get("note")));
        long prescriptionId = db.insert("INSERT INTO prescription(registration_id,patient_id,doctor_id,status,total_amount,note) "
                + "VALUES(:registration,:patient,:doctor,'ISSUED',:total,:note)", p);
        for (Map<String, Object> item : normalized) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("prescription", prescriptionId);
            db.insert("INSERT INTO prescription_item(prescription_id,drug_code,drug_name,dosage,frequency,days,quantity,unit_price,amount) "
                    + "VALUES(:prescription,:code,:name,:dosage,:frequency,:days,:quantity,:unitPrice,:amount)", row);
        }
        long billId = payments.createPrescriptionBill(prescriptionId, patientId, total);
        return prescriptionDetail(prescriptionId, patientId, billId);
    }

    @Transactional
    public Map<String, Object> recordExamResult(long examId, String result, long staffId) {
        if (text(result).isBlank() || result.length() > 4000) throw new BizException("检查结果不能为空且不能超过 4000 个字符");
        int updated = db.update("UPDATE exam_order SET status='RESULT_READY', result_text=:result, performed_by=:staff, "
                + "result_time=CURRENT_TIMESTAMP WHERE id=:id AND status IN ('ORDERED','SAMPLE_COLLECTED')",
                Map.of("id", examId, "result", result.trim(), "staff", staffId));
        if (updated == 0) throw new BizException("检查单不存在或当前状态不可回填");
        return db.queryOne("SELECT * FROM exam_order WHERE id=:id", Map.of("id", examId));
    }

    public List<Map<String, Object>> patientExams(long patientId) {
        return db.query("SELECT e.*, d.name doctor_name, b.id bill_id, b.status bill_status "
                + "FROM exam_order e LEFT JOIN doctor d ON d.id=e.doctor_id "
                + "LEFT JOIN bill b ON b.bill_type='EXAMINATION' AND b.source_id=e.id "
                + "WHERE e.patient_id=:patient ORDER BY e.id DESC", Map.of("patient", patientId));
    }

    public List<Map<String, Object>> doctorExams(long registrationId, long doctorId) {
        return db.query("SELECT e.*, b.id bill_id, b.status bill_status FROM exam_order e "
                + "LEFT JOIN bill b ON b.bill_type='EXAMINATION' AND b.source_id=e.id "
                + "WHERE e.registration_id=:registration AND e.doctor_id=:doctor ORDER BY e.id DESC",
                Map.of("registration", registrationId, "doctor", doctorId));
    }

    public List<Map<String, Object>> patientPrescriptions(long patientId) {
        List<Map<String, Object>> prescriptions = db.query("SELECT p.*, d.name doctor_name, b.id bill_id, b.status bill_status "
                + "FROM prescription p LEFT JOIN doctor d ON d.id=p.doctor_id "
                + "LEFT JOIN bill b ON b.bill_type='PRESCRIPTION' AND b.source_id=p.id "
                + "WHERE p.patient_id=:patient ORDER BY p.id DESC", Map.of("patient", patientId));
        for (Map<String, Object> prescription : prescriptions) {
            prescription.put("items", db.query("SELECT * FROM prescription_item WHERE prescription_id=:id ORDER BY id",
                    Map.of("id", prescription.get("id"))));
        }
        return prescriptions;
    }

    public List<Map<String, Object>> doctorPrescriptions(long registrationId, long doctorId) {
        List<Map<String, Object>> prescriptions = db.query("SELECT p.*, b.id bill_id, b.status bill_status FROM prescription p "
                + "LEFT JOIN bill b ON b.bill_type='PRESCRIPTION' AND b.source_id=p.id "
                + "WHERE p.registration_id=:registration AND p.doctor_id=:doctor ORDER BY p.id DESC",
                Map.of("registration", registrationId, "doctor", doctorId));
        for (Map<String, Object> prescription : prescriptions) {
            prescription.put("items", db.query("SELECT * FROM prescription_item WHERE prescription_id=:id ORDER BY id",
                    Map.of("id", prescription.get("id"))));
        }
        return prescriptions;
    }

    public List<Map<String, Object>> pharmacyQueue() {
        return db.query("SELECT p.id prescription_id, p.patient_id, p.registration_id, p.total_amount, p.status, "
                + "p.create_time, pp.name patient_name, d.name doctor_name, b.status bill_status "
                + "FROM prescription p LEFT JOIN patient_profile pp ON pp.id=p.patient_id "
                + "LEFT JOIN doctor d ON d.id=p.doctor_id "
                + "JOIN bill b ON b.bill_type='PRESCRIPTION' AND b.source_id=p.id AND b.status='PAID' "
                + "WHERE p.status IN ('ISSUED','DISPENSING') ORDER BY p.id", Map.of());
    }

    @Transactional
    public Map<String, Object> dispense(long prescriptionId, long staffId) {
        Map<String, Object> prescription = db.queryOne("SELECT id,patient_id,status FROM prescription WHERE id=:id FOR UPDATE",
                Map.of("id", prescriptionId));
        if (prescription == null) throw new BizException("处方不存在");
        if (!("ISSUED".equals(prescription.get("status")) || "DISPENSING".equals(prescription.get("status")))) {
            throw new BizException("处方当前不可发药");
        }
        Map<String, Object> bill = db.queryOne("SELECT status FROM bill WHERE bill_type='PRESCRIPTION' AND source_id=:id",
                Map.of("id", prescriptionId));
        if (bill == null || !"PAID".equals(bill.get("status"))) throw new BizException("处方费用尚未支付，不能发药");
        List<Map<String, Object>> items = db.query("SELECT id,quantity FROM prescription_item WHERE prescription_id=:id AND dispense_status='PENDING' FOR UPDATE",
                Map.of("id", prescriptionId));
        if (items.isEmpty()) throw new BizException("处方药品已全部发放");
        for (Map<String, Object> item : items) {
            db.insert("INSERT INTO pharmacy_dispense(prescription_id,item_id,pharmacist_id,quantity,status) "
                    + "VALUES(:prescription,:item,:staff,:quantity,'SUCCESS')", Map.of(
                    "prescription", prescriptionId, "item", item.get("id"), "staff", staffId, "quantity", item.get("quantity")));
            db.update("UPDATE prescription_item SET dispense_status='DISPENSED' WHERE id=:id AND dispense_status='PENDING'",
                    Map.of("id", item.get("id")));
        }
        db.update("UPDATE prescription SET status='DISPENSED' WHERE id=:id", Map.of("id", prescriptionId));
        return prescriptionDetail(prescriptionId, ((Number) prescription.get("patient_id")).longValue(), null);
    }

    private Map<String, Object> examDetail(long id, long patientId, long billId) {
        Map<String, Object> row = db.queryOne("SELECT e.*, b.status bill_status FROM exam_order e LEFT JOIN bill b "
                + "ON b.bill_type='EXAMINATION' AND b.source_id=e.id WHERE e.id=:id AND e.patient_id=:patient",
                Map.of("id", id, "patient", patientId));
        if (row != null) row.put("bill_id", billId);
        return row;
    }

    private Map<String, Object> prescriptionDetail(long id, long patientId, Long billId) {
        Map<String, Object> row = db.queryOne("SELECT p.*, b.status bill_status FROM prescription p LEFT JOIN bill b "
                + "ON b.bill_type='PRESCRIPTION' AND b.source_id=p.id WHERE p.id=:id AND p.patient_id=:patient",
                Map.of("id", id, "patient", patientId));
        if (row != null) {
            row.put("bill_id", billId);
            row.put("items", db.query("SELECT * FROM prescription_item WHERE prescription_id=:id ORDER BY id", Map.of("id", id)));
        }
        return row;
    }

    private void assertDoctorRegistration(long registrationId, long patientId, long doctorId) {
        Map<String, Object> row = db.queryOne("SELECT id FROM registration WHERE id=:registration AND patient_id=:patient AND doctor_id=:doctor "
                + "AND status IN ('PAID','DONE')", Map.of("registration", registrationId, "patient", patientId, "doctor", doctorId));
        if (row == null) throw new BizException("医嘱必须绑定当前医生负责的有效挂号");
    }

    private long requiredId(Map<String, Object> body, String key) {
        return requiredId(body.get(key), key);
    }

    private long requiredId(Object value, String key) {
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { throw new BizException("缺少有效字段：" + key); }
    }

    private String requiredText(Map<?, ?> body, String key, int max) { return requiredText(body.get(key), key, max); }

    private String requiredText(Object value, int max) { return requiredText(value, "药品字段", max); }

    private String requiredText(Object value, String key, int max) {
        String out = text(value);
        if (out.isBlank() || out.length() > max) throw new BizException(key + "不能为空且不能超过 " + max + " 个字符");
        return out;
    }

    private BigDecimal amount(Object value) { return nonNegativeAmount(value, "费用"); }

    private BigDecimal positiveAmount(Object value, String label) {
        BigDecimal out = nonNegativeAmount(value, label);
        if (out.signum() <= 0) throw new BizException(label + "必须大于 0");
        return out;
    }

    private BigDecimal nonNegativeAmount(Object value, String label) {
        try {
            BigDecimal raw = new BigDecimal(String.valueOf(value));
            if (raw.signum() < 0 || raw.scale() > 2) throw new NumberFormatException();
            return raw.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (Exception e) { throw new BizException(label + "必须是有效的两位小数"); }
    }

    private int positiveInt(Object value, String label) {
        try { int out = Integer.parseInt(String.valueOf(value)); if (out > 0 && out <= 365) return out; }
        catch (Exception ignored) { }
        throw new BizException(label + "必须是 1-365 的整数");
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
