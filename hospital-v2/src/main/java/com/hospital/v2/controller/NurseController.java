package com.hospital.v2.controller;

import com.hospital.v2.common.Result;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.service.ManualReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 护士端：只处理待人工导诊队列，不暴露管理员配置与观测能力。 */
@RestController
@RequestMapping("/api/nurse")
public class NurseController {
    private final ManualReviewService reviews;

    public NurseController(ManualReviewService reviews) { this.reviews = reviews; }

    @GetMapping("/triage/queue")
    public Result<?> queue() { return Result.ok(reviews.queue()); }

    @PostMapping("/triage/resolve")
    public Result<?> resolve(@RequestBody Map<String, Object> body,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(reviews.resolve(body, principal));
    }
}
