package com.hospital.v2.controller;

import com.hospital.v2.common.BizException;
import com.hospital.v2.common.Result;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.security.CurrentUserService;
import com.hospital.v2.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 患者账单中心：只允许患者查看和支付自己的账单。 */
@RestController
@RequestMapping("/api/patient/payment")
public class PaymentController {
    private final PaymentService payments;
    private final CurrentUserService currentUser;

    public PaymentController(PaymentService payments, CurrentUserService currentUser) {
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @GetMapping("/bills")
    public Result<?> bills(@RequestParam(required = false) String status,
                           @RequestParam(required = false) Long patientId,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(payments.listBillsByPatient(currentUser.patientId(principal, patientId), status));
    }

    @GetMapping("/bills/{billId}")
    public Result<?> detail(@PathVariable long billId,
                            @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(payments.getBillDetail(billId, currentUser.patientId(principal, null)));
    }

    @GetMapping("/balance")
    public Result<?> balance(@AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(payments.getBalance(currentUser.patientId(principal, null)));
    }

    @PostMapping("/pay")
    public Result<?> pay(@RequestBody Map<String, Object> body,
                         @AuthenticationPrincipal AuthPrincipal principal) {
        long billId = requiredLong(body.get("billId"), "billId");
        long patientId = currentUser.patientId(principal, null);
        String channel = String.valueOf(body.getOrDefault("channel", ""));
        if ("CASH".equalsIgnoreCase(channel)) {
            throw new BizException("现金支付需由收费窗口工作人员入账");
        }
        payments.payBill(billId, patientId, channel,
                body.get("tradeNo") == null ? null : String.valueOf(body.get("tradeNo")));
        return Result.ok(payments.getBillDetail(billId, patientId));
    }

    private long requiredLong(Object value, String field) {
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { throw new BizException("缺少有效的 " + field); }
    }
}
