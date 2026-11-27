package com.hospital.v2.controller;

import com.hospital.v2.common.Result;
import com.hospital.v2.service.MedicalRecordService;
import com.hospital.v2.service.ClinicalOrderService;
import com.hospital.v2.service.ManualReviewService;
import com.hospital.v2.security.AuthPrincipal;
import com.hospital.v2.security.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 医生端：门诊接诊（查看挂到自己名下的患者）+ 病历七段结构化书写（草稿/提交）。 */
@RestController
@RequestMapping("/api/doctor")
public class DoctorController {
    private final MedicalRecordService records;
    private final CurrentUserService currentUser;
    private final ClinicalOrderService orders;
    private final com.hospital.v2.service.CareEpisodeService episodes;
    private final ManualReviewService manualReviews;

    public DoctorController(MedicalRecordService records, CurrentUserService currentUser, ClinicalOrderService orders,
                            com.hospital.v2.service.CareEpisodeService episodes, ManualReviewService manualReviews) {
        this.records = records;
        this.currentUser = currentUser;
        this.orders = orders;
        this.episodes = episodes;
        this.manualReviews = manualReviews;
    }

    /** Agent 临床接管队列：医生处理高风险、低置信度和信息冲突任务。 */
    @GetMapping("/agent-tasks")
    public Result<?> agentTasks(@AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(episodes.doctorTasks(currentUser.doctorId(principal, null)));
    }

    /** 医生确认智能体接管任务，统一复用临床复核状态与审计逻辑。 */
    @PostMapping("/agent-tasks/resolve")
    public Result<?> resolveAgentTask(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(manualReviews.resolve(body, principal));
    }

    /** 我的接诊列表：挂号 + 患者档案 + 病历状态。 */
    @GetMapping("/registrations")
    public Result<?> registrations(@RequestParam(required = false) Long doctorId,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(records.doctorRegistrations(currentUser.doctorId(principal, doctorId)));
    }

    /** 保存病历草稿（七段结构化，可反复编辑）。 */
    @PostMapping("/record/save")
    public Result<?> saveDraft(@RequestBody Map<String, Object> body,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        Map<String, Object> safe = new java.util.LinkedHashMap<>(body);
        if (body.get("id") != null) {
            currentUser.assertDoctorOwnsRecord(principal, Long.parseLong(String.valueOf(body.get("id"))));
        }
        safe.put("doctor_id", currentUser.doctorId(principal,
                body.get("doctor_id") instanceof Number n ? n.longValue() : null));
        return Result.ok(Map.of("recordId", records.saveDraft(safe)));
    }

    /** 提交病历：草稿 → SUBMITTED 锁定；同时把挂号置为 DONE（完成就诊）。 */
    @PostMapping("/record/submit")
    public Result<?> submit(@RequestParam long recordId, @RequestParam long registrationId,
                            @AuthenticationPrincipal AuthPrincipal principal) {
        currentUser.assertDoctorOwnsRecord(principal, recordId);
        currentUser.assertDoctorOwnsRegistration(principal, registrationId);
        records.submit(recordId, registrationId);
        return Result.ok();
    }

    /** 完成就诊（不写病历直接结诊）。 */
    @PostMapping("/registration/done")
    public Result<?> done(@RequestParam long registrationId, @AuthenticationPrincipal AuthPrincipal principal) {
        currentUser.assertDoctorOwnsRegistration(principal, registrationId);
        records.finishVisit(registrationId);
        return Result.ok();
    }

    /** 病历详情。 */
    @GetMapping("/record")
    public Result<?> detail(@RequestParam long recordId, @AuthenticationPrincipal AuthPrincipal principal) {
        currentUser.assertDoctorOwnsRecord(principal, recordId);
        return Result.ok(records.detail(recordId));
    }

    @PostMapping("/exam-orders")
    public Result<?> createExam(@RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(orders.createExam(body, currentUser.doctorId(principal,
                body.get("doctorId") instanceof Number n ? n.longValue() : null)));
    }

    @PostMapping("/prescriptions")
    public Result<?> createPrescription(@RequestBody Map<String, Object> body,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(orders.createPrescription(body, currentUser.doctorId(principal,
                body.get("doctorId") instanceof Number n ? n.longValue() : null)));
    }

    @GetMapping("/exam-orders")
    public Result<?> examOrders(@RequestParam long registrationId,
                                @AuthenticationPrincipal AuthPrincipal principal) {
        currentUser.assertDoctorOwnsRegistration(principal, registrationId);
        return Result.ok(orders.doctorExams(registrationId, currentUser.doctorId(principal, null)));
    }

    @GetMapping("/prescriptions")
    public Result<?> prescriptions(@RequestParam long registrationId,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        currentUser.assertDoctorOwnsRegistration(principal, registrationId);
        return Result.ok(orders.doctorPrescriptions(registrationId, currentUser.doctorId(principal, null)));
    }
}
