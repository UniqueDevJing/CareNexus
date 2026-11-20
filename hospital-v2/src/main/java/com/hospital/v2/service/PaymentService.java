package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 账单与支付结算：只把“已落账”的资金变化标记为成功，余额扣减、流水和业务状态在同一事务内完成。
 * 外部微信/支付宝/银行卡的真实签名回调尚未接入，演示环境要求调用方提供已核验的 tradeNo。
 */
@Service
public class PaymentService {
    private static final List<String> CHANNELS = List.of("CASH", "WECHAT", "ALIPAY", "CARD", "BALANCE");
    private final Db db;

    public PaymentService(Db db) { this.db = db; }

    @Transactional
    public long createRegistrationBill(long registrationId, long patientId, BigDecimal amount) {
        return createBill("REGISTRATION", registrationId, patientId, amount, "门诊挂号费");
    }

    @Transactional
    public long createExaminationBill(long orderId, long patientId, BigDecimal amount) {
        return createBill("EXAMINATION", orderId, patientId, amount, "检查检验费用");
    }

    @Transactional
    public long createPrescriptionBill(long prescriptionId, long patientId, BigDecimal amount) {
        return createBill("PRESCRIPTION", prescriptionId, patientId, amount, "处方费用");
    }

    private long createBill(String type, long sourceId, long patientId, BigDecimal amount, String description) {
        requireAmount(amount);
        Map<String, Object> existing = db.queryOne(
                "SELECT id FROM bill WHERE bill_type=:type AND source_id=:source AND patient_id=:patient",
                Map.of("type", type, "source", sourceId, "patient", patientId));
        if (existing != null) return ((Number) existing.get("id")).longValue();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("patient", patientId); p.put("type", type); p.put("source", sourceId);
        p.put("amount", amount); p.put("description", description);
        return db.insert("INSERT INTO bill (patient_id,bill_type,source_id,amount,status,description) "
                + "VALUES (:patient,:type,:source,:amount,'UNPAID',:description)", p);
    }

    @Transactional
    public void payBill(long billId, long patientId, String channel, String tradeNo) {
        String method = text(channel).toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(method)) throw new BizException("不支持的支付渠道");
        if (!"CASH".equals(method) && !"BALANCE".equals(method) && text(tradeNo).isBlank()) {
            throw new BizException("第三方支付必须提供已核验的支付流水号");
        }

        Map<String, Object> bill = db.queryOne(
                "SELECT id,patient_id,bill_type,source_id,amount,status FROM bill "
                        + "WHERE id=:id AND patient_id=:patient FOR UPDATE",
                Map.of("id", billId, "patient", patientId));
        if (bill == null) throw new BizException("账单不存在或不属于当前患者");
        if (!"UNPAID".equals(String.valueOf(bill.get("status")))) {
            throw new BizException("账单当前不可支付");
        }
        BigDecimal amount = decimal(bill.get("amount"));
        if (amount.signum() <= 0) throw new BizException("账单金额无效");

        if ("BALANCE".equals(method)) {
            Map<String, Object> wallet = db.queryOne(
                    "SELECT balance FROM patient_profile WHERE id=:patient FOR UPDATE", Map.of("patient", patientId));
            if (wallet == null) throw new BizException("患者账户不存在");
            BigDecimal balance = decimal(wallet.get("balance"));
            if (balance.compareTo(amount) < 0) throw new BizException("账户余额不足，请更换支付方式");
            BigDecimal after = balance.subtract(amount);
            int updated = db.update("UPDATE patient_profile SET balance=:balance WHERE id=:patient AND balance>=:amount",
                    Map.of("balance", after, "patient", patientId, "amount", amount));
            if (updated == 0) throw new BizException("余额已发生变化，请刷新后重试");
            db.insert("INSERT INTO balance_ledger (patient_id,change_type,amount,balance_after,ref_bill_id,remark) "
                    + "VALUES (:patient,'DEDUCT',:amount,:after,:bill,:remark)", Map.of(
                    "patient", patientId, "amount", amount.negate(), "after", after,
                    "bill", billId, "remark", "支付" + bill.get("bill_type")));
        }

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("bill", billId); payment.put("patient", patientId); payment.put("amount", amount);
        payment.put("channel", method); payment.put("trade", text(tradeNo).isBlank() ? null : text(tradeNo));
        db.insert("INSERT INTO payment_record (bill_id,patient_id,transaction_type,amount,channel,trade_no,status) "
                + "VALUES (:bill,:patient,'PAYMENT',:amount,:channel,:trade,'SUCCESS')", payment);
        int updated = db.update("UPDATE bill SET status='PAID', paid_at=CURRENT_TIMESTAMP WHERE id=:id AND status='UNPAID'",
                Map.of("id", billId));
        if (updated == 0) throw new BizException("账单已被其他请求处理，请刷新");
        if ("REGISTRATION".equals(String.valueOf(bill.get("bill_type")))) {
            db.update("UPDATE registration SET payment_status='PAID' WHERE id=:id AND patient_id=:patient",
                    Map.of("id", bill.get("source_id"), "patient", patientId));
        }
    }

    @Transactional
    public void refundBill(long billId, String reason) {
        Map<String, Object> bill = db.queryOne(
                "SELECT id,patient_id,bill_type,source_id,amount,status FROM bill WHERE id=:id FOR UPDATE",
                Map.of("id", billId));
        if (bill == null) throw new BizException("账单不存在");
        if (!"PAID".equals(String.valueOf(bill.get("status")))) throw new BizException("只有已支付账单可以退费");
        Map<String, Object> payment = db.queryOne(
                "SELECT channel FROM payment_record WHERE bill_id=:bill AND transaction_type='PAYMENT' "
                        + "AND status='SUCCESS' ORDER BY id DESC LIMIT 1", Map.of("bill", billId));
        if (payment == null) throw new BizException("支付流水不存在，无法退费");

        BigDecimal amount = decimal(bill.get("amount"));
        if ("BALANCE".equals(String.valueOf(payment.get("channel")))) {
            Map<String, Object> wallet = db.queryOne(
                    "SELECT balance FROM patient_profile WHERE id=:patient FOR UPDATE", Map.of("patient", bill.get("patient_id")));
            if (wallet == null) throw new BizException("患者账户不存在");
            BigDecimal after = decimal(wallet.get("balance")).add(amount);
            db.update("UPDATE patient_profile SET balance=:balance WHERE id=:patient",
                    Map.of("balance", after, "patient", bill.get("patient_id")));
            db.insert("INSERT INTO balance_ledger (patient_id,change_type,amount,balance_after,ref_bill_id,remark) "
                    + "VALUES (:patient,'REFUND',:amount,:after,:bill,:remark)", Map.of(
                    "patient", bill.get("patient_id"), "amount", amount, "after", after,
                    "bill", billId, "remark", text(reason).isBlank() ? "账单退费" : text(reason)));
        }
        db.insert("INSERT INTO payment_record (bill_id,patient_id,transaction_type,amount,channel,status,refund_reason) "
                + "VALUES (:bill,:patient,'REFUND',:amount,:channel,'SUCCESS',:reason)", Map.of(
                "bill", billId, "patient", bill.get("patient_id"), "amount", amount,
                "channel", payment.get("channel"), "reason", text(reason)));
        db.update("UPDATE bill SET status='REFUNDED' WHERE id=:id AND status='PAID'", Map.of("id", billId));
        if ("REGISTRATION".equals(String.valueOf(bill.get("bill_type")))) {
            db.update("UPDATE registration SET payment_status='REFUNDED' WHERE id=:id", Map.of("id", bill.get("source_id")));
        }
    }

    public List<Map<String, Object>> listBillsByPatient(long patientId, String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("patient", patientId);
        String sql = "SELECT b.*, r.work_date, r.period, dep.name department_name, doc.name doctor_name "
                + "FROM bill b LEFT JOIN registration r ON r.id=b.source_id AND b.bill_type='REGISTRATION' "
                + "LEFT JOIN department dep ON dep.id=r.department_id LEFT JOIN doctor doc ON doc.id=r.doctor_id "
                + "WHERE b.patient_id=:patient";
        if (status != null && !status.isBlank()) {
            if (!List.of("UNPAID", "PAID", "REFUNDED", "CANCELLED").contains(status)) throw new BizException("账单状态无效");
            sql += " AND b.status=:status"; p.put("status", status);
        }
        return db.query(sql + " ORDER BY b.id DESC", p);
    }

    public Map<String, Object> getBillDetail(long billId, long patientId) {
        Map<String, Object> bill = db.queryOne("SELECT * FROM bill WHERE id=:id AND patient_id=:patient",
                Map.of("id", billId, "patient", patientId));
        if (bill == null) throw new BizException("账单不存在或不属于当前患者");
        Map<String, Object> out = new LinkedHashMap<>(bill);
        out.put("payments", db.query("SELECT id,transaction_type,amount,channel,trade_no,status,refund_reason,created_at "
                + "FROM payment_record WHERE bill_id=:bill ORDER BY id DESC", Map.of("bill", billId)));
        return out;
    }

    public Map<String, Object> getBalance(long patientId) {
        Map<String, Object> row = db.queryOne("SELECT balance FROM patient_profile WHERE id=:patient", Map.of("patient", patientId));
        if (row == null) throw new BizException("患者账户不存在");
        return Map.of("balance", decimal(row.get("balance")));
    }

    public Map<String, Object> getRevenueStats(String period, String start, String end) {
        LocalDate from = LocalDate.parse(start);
        LocalDate to = LocalDate.parse(end);
        if (to.isBefore(from)) throw new BizException("统计结束日期不能早于开始日期");
        String mode = "monthly".equalsIgnoreCase(period) ? "monthly" : "daily";
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = db.query("SELECT transaction_type,amount,created_at FROM payment_record "
                + "WHERE status='SUCCESS' AND created_at>=:from AND created_at<:to ORDER BY created_at", Map.of(
                "from", fromTime, "to", toExclusive));
        BigDecimal paid = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        Map<String, BigDecimal> series = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            BigDecimal amount = decimal(row.get("amount"));
            boolean refund = "REFUND".equals(String.valueOf(row.get("transaction_type")));
            if (refund) refunded = refunded.add(amount); else paid = paid.add(amount);
            Object time = row.get("created_at");
            LocalDate date = time instanceof java.sql.Timestamp ts
                    ? ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.parse(String.valueOf(time).substring(0, 10));
            String key = "monthly".equals(mode) ? YearMonth.from(date).toString() : date.toString();
            series.merge(key, refund ? amount.negate() : amount, BigDecimal::add);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", mode); out.put("start", start); out.put("end", end);
        out.put("paid", paid); out.put("refunded", refunded); out.put("net", paid.subtract(refunded));
        out.put("series", series);
        return out;
    }

    private void requireAmount(BigDecimal amount) {
        if (amount == null || amount.scale() > 2 || amount.signum() <= 0) throw new BizException("金额必须为两位小数以内的正数");
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
