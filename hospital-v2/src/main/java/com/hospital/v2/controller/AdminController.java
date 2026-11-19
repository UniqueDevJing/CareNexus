package com.hospital.v2.controller;

import com.hospital.v2.common.BizException;
import com.hospital.v2.common.Result;
import com.hospital.v2.dao.Db;
import com.hospital.v2.service.DashboardService;
import com.hospital.v2.service.KnowledgeService;
import com.hospital.v2.service.ToolCenter;
import com.hospital.v2.service.UsageService;
import com.hospital.v2.security.AuthPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端 REST：工具注册表、知识库、看板、Agent 时间线、AI 观测、基础数据。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ToolCenter tools;
    private final KnowledgeService knowledge;
    private final DashboardService dashboard;
    private final UsageService usage;
    private final Db db;
    private final PasswordEncoder passwords;

    public AdminController(ToolCenter tools, KnowledgeService knowledge,
                           DashboardService dashboard, UsageService usage, Db db,
                           PasswordEncoder passwords) {
        this.tools = tools;
        this.knowledge = knowledge;
        this.dashboard = dashboard;
        this.usage = usage;
        this.db = db;
        this.passwords = passwords;
    }

    // ---- 工具中心 ----
    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> tools() {
        return Result.ok(db.query("SELECT * FROM mcp_tool ORDER BY sort", Map.of()));
    }

    @PostMapping("/tools/toggle")
    public Result<?> toggle(@RequestParam long id) {
        db.update("UPDATE mcp_tool SET enabled = 1 - enabled WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    @PostMapping("/tools/test")
    public Result<?> testTool(@RequestParam String code, @RequestBody(required = false) Map<String, Object> args) {
        String argJson;
        try {
            argJson = args == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
        } catch (Exception e) {
            argJson = "{}";
        }
        String result = tools.execute(code, argJson, null, null, "manual");
        return Result.ok(Map.of("result", result));
    }

    // ---- 知识库 RAG ----
    @GetMapping("/knowledge/bases")
    public Result<?> bases() { return Result.ok(knowledge.listBases()); }

    @GetMapping("/knowledge/docs")
    public Result<?> docs(@RequestParam long baseId) { return Result.ok(knowledge.listDocs(baseId)); }

    @PostMapping("/knowledge/add")
    public Result<?> addDoc(@RequestParam long baseId, @RequestParam String title, @RequestParam String content) {
        return Result.ok(Map.of("docId", knowledge.addDoc(baseId, title, content)));
    }

    @GetMapping("/knowledge/retrieve")
    public Result<?> retrieve(@RequestParam String q, @RequestParam(required = false) Long baseId,
                              @RequestParam(defaultValue = "4") int topK) {
        return Result.ok(knowledge.retrieve(q, baseId, topK));
    }

    // ---- 看板与观测 ----
    @GetMapping("/dashboard/overview")
    public Result<?> overview() { return Result.ok(dashboard.overview()); }

    @GetMapping("/dashboard/by-department")
    public Result<?> byDept() { return Result.ok(dashboard.byDepartment()); }

    @GetMapping("/dashboard/runs")
    public Result<?> runs() { return Result.ok(dashboard.recentRuns()); }

    @GetMapping("/dashboard/timeline")
    public Result<?> timeline(@RequestParam long runId) { return Result.ok(dashboard.timeline(runId)); }

    @GetMapping("/dashboard/tool-logs")
    public Result<?> toolLogs(@RequestParam long runId) { return Result.ok(dashboard.toolLogs(runId)); }

    @GetMapping("/dashboard/manual-review")
    public Result<?> manualReview() { return Result.ok(dashboard.manualReview()); }

    /** 人工导诊最小闭环：确认可预约或退回人工处理，生产环境应单独授予导诊/护士角色。 */
    @PostMapping("/triage/manual-review")
    public Result<?> resolveManualReview(@RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        long suggestionId = Long.parseLong(String.valueOf(body.get("suggestionId")));
        String action = String.valueOf(body.getOrDefault("action", "")).toUpperCase(java.util.Locale.ROOT);
        Map<String, Object> suggestion = db.queryOne(
                "SELECT id, session_id, department_id, doctor_id, red_flag, status, reason FROM triage_suggestion WHERE id=:id",
                Map.of("id", suggestionId));
        if (suggestion == null) throw new BizException("分诊建议不存在");
        if (!"PENDING".equals(String.valueOf(suggestion.get("status")))) {
            throw new BizException("该分诊建议已处理，无需重复操作");
        }
        String note = String.valueOf(body.getOrDefault("note", "人工导诊处理"));
        if (note.length() > 220) note = note.substring(0, 220);
        String oldReason = String.valueOf(suggestion.getOrDefault("reason", ""));
        String reason = (oldReason + "（" + note + "）");
        if (reason.length() > 500) reason = reason.substring(0, 500);
        if ("CONFIRM".equals(action)) {
            if (Integer.valueOf(String.valueOf(suggestion.getOrDefault("red_flag", 0))) == 1) {
                throw new BizException("命中风险规则的患者不能直接确认普通预约，请转急诊或人工医生处理");
            }
            long dept = body.get("departmentId") == null
                    ? ((Number) suggestion.get("department_id")).longValue()
                    : Long.parseLong(String.valueOf(body.get("departmentId")));
            long doctor = body.get("doctorId") == null
                    ? ((Number) suggestion.get("doctor_id")).longValue()
                    : Long.parseLong(String.valueOf(body.get("doctorId")));
            Map<String, Object> doctorRow = db.queryOne(
                    "SELECT id FROM doctor WHERE id=:doctor AND department_id=:dept",
                    Map.of("doctor", doctor, "dept", dept));
            if (doctorRow == null) throw new BizException("医生不属于所选科室");
            Map<String, Object> slot = db.queryOne(
                    "SELECT id FROM doctor_schedule WHERE doctor_id=:doctor AND status=1 AND remain_num>0 "
                            + "AND work_date>=CURRENT_DATE ORDER BY work_date, period LIMIT 1",
                    Map.of("doctor", doctor));
            if (slot == null) throw new BizException("当前医生暂无可用号源，请改派医生或联系人工服务");
            db.update("UPDATE triage_suggestion SET department_id=:dept, doctor_id=:doctor, status='CONFIRMED', reason=:reason WHERE id=:id",
                    Map.of("dept", dept, "doctor", doctor, "reason", reason, "id", suggestionId));
            if (suggestion.get("session_id") != null) {
                db.update("UPDATE pre_triage_session SET locked=1, status='LOCKED', summary=:summary WHERE id=:id",
                        Map.of("summary", "人工导诊已确认，可继续预约", "id", suggestion.get("session_id")));
            }
        } else if ("REJECT".equals(action)) {
            db.update("UPDATE triage_suggestion SET status='REJECTED', reason=:reason WHERE id=:id",
                    Map.of("reason", reason, "id", suggestionId));
        } else {
            throw new BizException("只支持 CONFIRM 或 REJECT");
        }
        db.insert("INSERT INTO operation_audit (operator, action, target, detail) VALUES (:operator,:action,:target,:detail)",
                Map.of("operator", principal == null ? "admin" : principal.username(),
                        "action", "MANUAL_TRIAGE_" + action, "target", "triage_suggestion:" + suggestionId,
                        "detail", reason));
        return Result.ok(Map.of("suggestionId", suggestionId, "status", "CONFIRM".equals(action) ? "CONFIRMED" : "REJECTED"));
    }

    @GetMapping("/usage")
    public Result<?> usage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", usage.summary());
        m.put("byCase", usage.byCase());
        return Result.ok(m);
    }

    // ---- 基础数据 ----
    @GetMapping("/departments")
    public Result<?> departments() { return Result.ok(db.query("SELECT * FROM department ORDER BY id", Map.of())); }

    @GetMapping("/doctors")
    public Result<?> doctors(@RequestParam(required = false) Long departmentId) {
        if (departmentId == null) return Result.ok(db.query("SELECT * FROM doctor ORDER BY id", Map.of()));
        return Result.ok(db.query("SELECT * FROM doctor WHERE department_id=:d ORDER BY id", Map.of("d", departmentId)));
    }

    @GetMapping("/schedules")
    public Result<?> schedules(@RequestParam(required = false) Long doctorId, @RequestParam(required = false) String date) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("doc", doctorId);
        p.put("date", date);
        String sql = "SELECT * FROM doctor_schedule WHERE 1=1";
        if (doctorId != null) sql += " AND doctor_id=:doc";
        if (date != null) sql += " AND work_date=:date";
        sql += " ORDER BY work_date, period";
        return Result.ok(db.query(sql, p));
    }

    @GetMapping("/symptom-tags")
    public Result<?> symptomTags() { return Result.ok(db.query("SELECT * FROM symptom_tag ORDER BY id", Map.of())); }

    @GetMapping("/red-flags")
    public Result<?> redFlags() { return Result.ok(db.query("SELECT * FROM red_flag_rule ORDER BY level", Map.of())); }

    @GetMapping("/ai-models")
    public Result<?> aiModels() { return Result.ok(db.query("SELECT id,name,provider,base_url,model,purpose,prompt_template,enabled,sort,"
            + "CASE WHEN api_key IS NULL OR api_key='' THEN 0 ELSE 1 END has_api_key,create_time "
            + "FROM ai_model_config ORDER BY sort", Map.of())); }

    // ==================== 产品运营能力：管理 CRUD / 顺延 / AI 配置 / 工具注册 / 上传 / 看板增强 ====================

    // ---- 通用 CRUD：科室 / 医生 / 排班 / 患者 / 症状标签 / 危急规则 ----
    @PostMapping("/departments/save")
    public Result<?> deptSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO department (name, code, description, icon) "
                    + "VALUES (:name,:code,:description,:icon)", body)));
        }
        db.update("UPDATE department SET name=:name, code=:code, description=:description, icon=:icon WHERE id=:id", body);
        return Result.ok();
    }

    @PostMapping("/doctors/save")
    public Result<?> doctorSave(@RequestBody Map<String, Object> body) {
        body.put("dept", body.get("department_id") == null ? null : Long.valueOf(String.valueOf(body.get("department_id"))));
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            // Generate a one-time password when no invite password is supplied; never persist a default plaintext password.
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("username", String.valueOf(body.getOrDefault("name", "doctor" + System.currentTimeMillis())));
            u.put("real_name", body.get("name"));
            u.put("role", "doctor");
            u.put("phone", body.getOrDefault("phone", ""));
            String temporaryPassword = String.valueOf(body.getOrDefault("initial_password", "")).trim();
            if (temporaryPassword.isBlank()) temporaryPassword = temporaryPassword();
            if (temporaryPassword.length() < 12 || temporaryPassword.length() > 72) throw new BizException("医生初始密码需为 12-72 位");
            u.put("password", passwords.encode(temporaryPassword));
            long uid = db.insert("INSERT INTO user_account (username, password, real_name, role, phone) VALUES (:username,:password,:real_name,:role,:phone)", u);
            body.put("user_id", uid);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", db.insert("INSERT INTO doctor (name, department_id, title, specialty, good_at, user_id) "
                    + "VALUES (:name,:dept,:title,:specialty,:good_at,:user_id)", body));
            result.put("userId", uid);
            result.put("temporaryPassword", temporaryPassword);
            return Result.ok(result);
        }
        db.update("UPDATE doctor SET name=:name, department_id=:dept, title=:title, specialty=:specialty, good_at=:good_at WHERE id=:id", body);
        return Result.ok();
    }

    private String temporaryPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @PostMapping("/schedules/save")
    public Result<?> scheduleSave(@RequestBody Map<String, Object> body) {
        body.put("doc", body.get("doctor_id") == null ? null : Long.valueOf(String.valueOf(body.get("doctor_id"))));
        body.put("dept", body.get("department_id") == null ? null : Long.valueOf(String.valueOf(body.get("department_id"))));
        int total = body.get("total_num") == null ? 20 : Integer.parseInt(String.valueOf(body.get("total_num")));
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO doctor_schedule (doctor_id, department_id, work_date, period, total_num, remain_num, status) "
                    + "VALUES (:doc,:dept,:work_date,:period," + total + "," + total + ",1)", body)));
        }
        db.update("UPDATE doctor_schedule SET doctor_id=:doc, department_id=:dept, work_date=:work_date, period=:period, "
                + "total_num=" + total + " WHERE id=:id", body);
        return Result.ok();
    }

    /** 排班一键顺延：把某医生从指定日期起的排班整体复制顺延 N 天（重复的跳过）。 */
    @PostMapping("/schedules/postpone")
    public Result<?> schedulePostpone(@RequestParam long doctorId, @RequestParam String fromDate, @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> rows = db.query(
                "SELECT department_id, work_date, period, total_num FROM doctor_schedule "
                        + "WHERE doctor_id=:doc AND work_date>=:from AND status=1",
                Map.of("doc", doctorId, "from", fromDate));
        int created = 0;
        for (Map<String, Object> r : rows) {
            String nd = java.time.LocalDate.parse(String.valueOf(r.get("work_date"))).plusDays(days).toString();
            Map<String, Object> dup = db.queryOne("SELECT COUNT(*) c FROM doctor_schedule WHERE doctor_id=:doc AND work_date=:d AND period=:p",
                    Map.of("doc", doctorId, "d", nd, "p", r.get("period")));
            if (dup != null && ((Number) dup.get("c")).intValue() > 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doc", doctorId);
            m.put("dept", r.get("department_id"));
            m.put("d", nd);
            m.put("p", r.get("period"));
            m.put("t", r.get("total_num"));
            db.insert("INSERT INTO doctor_schedule (doctor_id, department_id, work_date, period, total_num, remain_num, status) "
                    + "VALUES (:doc,:dept,:d,:p,:t,:t,1)", m);
            created++;
        }
        return Result.ok(Map.of("created", created));
    }

    @PostMapping("/schedules/delete")
    public Result<?> scheduleDelete(@RequestParam long id) {
        db.update("DELETE FROM doctor_schedule WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    @PostMapping("/patients/save")
    public Result<?> patientSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO patient_profile (name, gender, age, allergic_history, past_history) "
                    + "VALUES (:name,:gender,:age,:allergic_history,:past_history)", body)));
        }
        db.update("UPDATE patient_profile SET name=:name, gender=:gender, age=:age, allergic_history=:allergic_history, "
                + "past_history=:past_history WHERE id=:id", body);
        return Result.ok();
    }

    @PostMapping("/symptom-tags/save")
    public Result<?> tagSave(@RequestBody Map<String, Object> body) {
        body.put("dept", body.get("department_id") == null ? null : Long.valueOf(String.valueOf(body.get("department_id"))));
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO symptom_tag (name, department_id, description) "
                    + "VALUES (:name,:dept,:description)", body)));
        }
        db.update("UPDATE symptom_tag SET name=:name, department_id=:dept, description=:description WHERE id=:id", body);
        return Result.ok();
    }

    @PostMapping("/red-flags/save")
    public Result<?> flagSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO red_flag_rule (name, keywords, level, advice) "
                    + "VALUES (:name,:keywords,:level,:advice)", body)));
        }
        db.update("UPDATE red_flag_rule SET name=:name, keywords=:keywords, level=:level, advice=:advice WHERE id=:id", body);
        return Result.ok();
    }

    // ---- 症状标签库：症状→科室映射 + 删除 ----

    @GetMapping("/symptom-maps")
    public Result<?> symptomMaps() {
        return Result.ok(db.query("SELECT m.*, d.name department_name FROM symptom_department_map m "
                + "LEFT JOIN department d ON d.id=m.department_id ORDER BY m.symptom_keyword, m.weight DESC", Map.of()));
    }

    @PostMapping("/symptom-maps/save")
    public Result<?> mapSave(@RequestBody Map<String, Object> body) {
        body.put("k", String.valueOf(body.get("symptom_keyword")));
        body.put("dept", Long.valueOf(String.valueOf(body.get("department_id"))));
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            Map<String, Object> dup = db.queryOne("SELECT id FROM symptom_department_map "
                    + "WHERE symptom_keyword=:k AND department_id=:dept",
                    Map.of("k", String.valueOf(body.get("symptom_keyword")), "dept", body.get("dept")));
            if (dup != null) throw new BizException("该关键词已映射到此科室");
            return Result.ok(Map.of("id", db.insert("INSERT INTO symptom_department_map (symptom_keyword, department_id, weight) "
                    + "VALUES (:k,:dept,:weight)", body)));
        }
        db.update("UPDATE symptom_department_map SET symptom_keyword=:k, department_id=:dept, weight=:weight WHERE id=:id", body);
        return Result.ok();
    }

    @PostMapping("/symptom-maps/delete")
    public Result<?> mapDelete(@RequestParam long id) {
        db.update("DELETE FROM symptom_department_map WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    @PostMapping("/symptom-tags/delete")
    public Result<?> tagDelete(@RequestParam long id) {
        db.update("DELETE FROM symptom_tag WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    @PostMapping("/red-flags/delete")
    public Result<?> flagDelete(@RequestParam long id) {
        db.update("DELETE FROM red_flag_rule WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    @GetMapping("/patients")
    public Result<?> patients() { return Result.ok(db.query("SELECT * FROM patient_profile ORDER BY id", Map.of())); }

    // ---- AI 模型配置：保存 + 连通性测试 ----
    @PostMapping("/ai-models/save")
    public Result<?> modelSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO ai_model_config (name, provider, base_url, model, purpose, prompt_template, enabled, sort) "
                    + "VALUES (:name,:provider,:base_url,:model,:purpose,:prompt_template,:enabled,:sort)", body)));
        }
        db.update("UPDATE ai_model_config SET name=:name, provider=:provider, base_url=:base_url, model=:model, "
                + "purpose=:purpose, prompt_template=:prompt_template, enabled=:enabled, sort=:sort WHERE id=:id", body);
        return Result.ok();
    }

    /** 连通性测试：用请求里的模型参数实际调用一次对话接口，返回耗时与回复。 */
    @PostMapping("/ai-models/test")
    public Result<?> modelTest(@RequestBody Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        try {
            String baseUrl = String.valueOf(body.getOrDefault("base_url", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
            String model = String.valueOf(body.getOrDefault("model", "qwen-flash"));
            String apiKey = System.getenv("LLM_API_KEY");
            if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("DASHSCOPE_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                return Result.ok(Map.of("ok", false, "ms", 0, "reply", "未配置 API Key（环境变量 LLM_API_KEY）"));
            }
            dev.langchain4j.model.chat.ChatModel probe = dev.langchain4j.model.openai.OpenAiChatModel.builder()
                    .baseUrl(baseUrl).apiKey(apiKey).modelName(model).temperature(0.0)
                    .build();
            String reply = probe.chat(dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(List.of(dev.langchain4j.data.message.SystemMessage.from("你是连通性探针"),
                            dev.langchain4j.data.message.UserMessage.from("请只回复两个字：正常")))
                    .build()).aiMessage().text();
            return Result.ok(Map.of("ok", true, "ms", (int) (System.currentTimeMillis() - t0), "reply", reply));
        } catch (Exception e) {
            return Result.ok(Map.of("ok", false, "ms", (int) (System.currentTimeMillis() - t0), "reply",
                    String.valueOf(e.getMessage())));
        }
    }

    // ---- 工具中心：注册（新增/编辑） ----
    @PostMapping("/tools/save")
    public Result<?> toolSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        if (id == null) {
            return Result.ok(Map.of("id", db.insert("INSERT INTO mcp_tool (tool_code, name, description, param_schema, enabled, sort) "
                    + "VALUES (:tool_code,:name,:description,:param_schema,:enabled,:sort)", body)));
        }
        db.update("UPDATE mcp_tool SET tool_code=:tool_code, name=:name, description=:description, "
                + "param_schema=:param_schema, enabled=:enabled, sort=:sort WHERE id=:id", body);
        return Result.ok();
    }

    @PostMapping("/tools/delete")
    public Result<?> toolDelete(@RequestParam long id) {
        db.update("DELETE FROM mcp_tool WHERE id=:id", Map.of("id", id));
        return Result.ok();
    }

    // ---- 知识库：文件上传解析（txt/md） ----
    @PostMapping(value = "/knowledge/upload", consumes = "multipart/form-data")
    public Result<?> uploadDoc(@RequestParam long baseId, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        String name = file.getOriginalFilename() == null ? "doc.txt" : file.getOriginalFilename();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".txt") || lower.endsWith(".md"))) {
            throw new BizException("知识库上传仅支持 TXT 或 Markdown 文件");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new BizException("知识库文件不能超过 10 MB");
        }
        String text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        // 去掉 md 标记符号，保留正文
        text = text.replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("\\*+", "").replaceAll("`+", "");
        if (text.isBlank()) return Result.fail(400, "文件内容为空");
        return Result.ok(Map.of("docId", knowledge.addDoc(baseId, name, text), "chars", text.length()));
    }

    // ---- 看板增强：挂号量 / 号源使用率 / 按医生汇总 ----
    @GetMapping("/dashboard/enhanced")
    public Result<?> dashboardEnhanced() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> reg = db.queryOne("SELECT COUNT(*) registrations FROM registration", Map.of());
        Map<String, Object> sched = db.queryOne(
                "SELECT SUM(total_num) total, SUM(total_num - remain_num) used FROM doctor_schedule WHERE status=1", Map.of());
        m.put("registrations", reg == null ? 0 : reg.get("registrations"));
        long total = sched == null || sched.get("total") == null ? 0 : ((Number) sched.get("total")).longValue();
        long used = sched == null || sched.get("used") == null ? 0 : ((Number) sched.get("used")).longValue();
        m.put("slotTotal", total);
        m.put("slotUsed", used);
        m.put("slotUsage", total == 0 ? "0%" : String.format("%.1f%%", used * 100.0 / total));
        m.put("byDoctor", db.query(
                "SELECT d.id, d.name, d.title, dep.name department, COUNT(r.id) regs, "
                        + "SUM(CASE WHEN s.status=1 THEN s.total_num ELSE 0 END) slots, "
                        + "SUM(CASE WHEN s.status=1 THEN s.total_num - s.remain_num ELSE 0 END) used "
                        + "FROM doctor d LEFT JOIN department dep ON dep.id=d.department_id "
                        + "LEFT JOIN registration r ON r.doctor_id=d.id "
                        + "LEFT JOIN doctor_schedule s ON s.doctor_id=d.id "
                        + "GROUP BY d.id, d.name, d.title, dep.name ORDER BY regs DESC, d.id", Map.of()));
        return Result.ok(m);
    }
}
