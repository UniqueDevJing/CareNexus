package com.hospital.v2.controller;

import com.hospital.v2.common.Result;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.service.ManualReviewService;
import com.hospital.v2.service.ClinicalOrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 护士端：只处理待人工导诊队列，不暴露管理员配置与观测能力。 */
@RestController
@RequestMapping("/api/nurse")
public class NurseController {
    private final ManualReviewService reviews;
    private final ClinicalOrderService orders;

    public NurseController(ManualReviewService reviews, ClinicalOrderService orders) {
        this.reviews = reviews;
        this.orders = orders;
    }

    @GetMapping("/triage/queue")
    public Result<?> queue() { return Result.ok(reviews.queue()); }

    @PostMapping("/triage/resolve")
    public Result<?> resolve(@RequestBody Map<String, Object> body,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(reviews.resolve(body, principal));
    }

    /** 护士/药房工作台：只展示已支付且尚未发放的处方。 */
    @GetMapping("/pharmacy/queue")
    public Result<?> pharmacyQueue() { return Result.ok(orders.pharmacyQueue()); }

    @PostMapping("/pharmacy/dispense")
    public Result<?> dispense(@RequestParam long prescriptionId,
                              @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(orders.dispense(prescriptionId, principal.userId()));
    }

    @PostMapping("/exam-orders/result")
    public Result<?> recordExamResult(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        long examId = Long.parseLong(String.valueOf(body.get("examId")));
        return Result.ok(orders.recordExamResult(examId, String.valueOf(body.getOrDefault("result", "")), principal.userId()));
    }
}
