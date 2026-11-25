package com.hospital.v2.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hospital.v2.common.JsonUtils;
import com.hospital.v2.config.HospitalProps;
import com.hospital.v2.dao.Db;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控工具中心。
 * 工具的参数契约由代码固定，数据库只保留管理员启停开关；这样不会因为一条
 * 配置记录缺少参数定义而让 Agent 失去可调用工具，执行日志仍统一写入审计表。
 */
@Service
public class ToolCenter {
    private final Db db;
    private final KnowledgeService knowledge;
    private final HospitalProps props;

    public ToolCenter(Db db, KnowledgeService knowledge, HospitalProps props) {
        this.db = db;
        this.knowledge = knowledge;
        this.props = props;
    }

    /** 构建稳定的工具契约，再应用管理员的启停开关。 */
    public List<ToolSpecification> buildSpecs() {
        Map<String, ToolSpecification> registry = new LinkedHashMap<>();
        registry.put("symptom_tag_search", ToolSpecification.builder()
                .name("symptom_tag_search").description("根据症状关键词搜索症状标签和推荐科室")
                .parameters(JsonObjectSchema.builder().addStringProperty("keyword", "症状关键词").build()).build());
        registry.put("department_query", ToolSpecification.builder()
                .name("department_query").description("按关键词查询医院科室信息")
                .parameters(JsonObjectSchema.builder().addStringProperty("keyword", "科室名称或症状关键词").build()).build());
        registry.put("doctor_query", ToolSpecification.builder()
                .name("doctor_query").description("查询指定科室的医生列表及其擅长方向")
                .parameters(JsonObjectSchema.builder().addIntegerProperty("department_id", "科室ID")
                        .addStringProperty("title", "医生职称筛选，可选").build()).build());
        registry.put("schedule_query", ToolSpecification.builder()
                .name("schedule_query").description("查询指定医生在指定日期的可用号源")
                .parameters(JsonObjectSchema.builder().addIntegerProperty("doctor_id", "医生ID")
                        .addStringProperty("work_date", "日期 yyyy-MM-dd")
                        .addStringProperty("period", "AM/PM，可选").build()).build());
        registry.put("knowledge_retrieve", ToolSpecification.builder()
                .name("knowledge_retrieve").description("检索与分诊相关的循证知识片段")
                .parameters(JsonObjectSchema.builder().addStringProperty("query", "检索问题")
                        .addIntegerProperty("top_k", "返回数量，可选").build()).build());

        try {
            List<Map<String, Object>> enabledRows = db.query(
                    "SELECT tool_code FROM mcp_tool WHERE enabled=1 ORDER BY sort", Map.of());
            Set<String> enabled = new LinkedHashSet<>();
            for (Map<String, Object> row : enabledRows) enabled.add(String.valueOf(row.get("tool_code")));
            return registry.entrySet().stream()
                    .filter(entry -> enabled.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        } catch (RuntimeException ignored) {
            // 兼容旧库或精简库：工具契约仍可工作，管理员开关在迁移完成后恢复。
        }
        return new ArrayList<>(registry.values());
    }

    /** 执行工具：解析入参 → 分发 → 记日志 → 返回结果文本。 */
    public String execute(String toolCode, String argsJson, Long runId, Long stepId, String source) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> args = JsonUtils.fromJson(argsJson == null ? "{}" : argsJson, new TypeReference<>() {});
        if (args == null) args = new LinkedHashMap<>();
        try {
            String result = switch (toolCode) {
                case "department_query" -> departmentQuery(str(args, "keyword"));
                case "doctor_query" -> doctorQuery(num(args, "department_id"), str(args, "title"));
                case "schedule_query" -> scheduleQuery(num(args, "doctor_id"), str(args, "work_date"), str(args, "period"));
                case "symptom_tag_search" -> symptomSearch(str(args, "keyword"));
                case "knowledge_retrieve" -> knowledgeRetrieve(str(args, "query"), num(args, "top_k"));
                default -> throw new IllegalArgumentException("未知工具: " + toolCode);
            };
            writeLog(toolCode, argsJson, result, "SUCCESS", (int) (System.currentTimeMillis() - t0), runId, stepId, source);
            return result;
        } catch (Exception e) {
            String err = "{\"error\":\"" + e.getMessage() + "\"}";
            writeLog(toolCode, argsJson, err, "FAILED", (int) (System.currentTimeMillis() - t0), runId, stepId, source);
            return err;
        }
    }

    private String departmentQuery(String kw) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kw", "%" + (kw == null ? "" : kw) + "%");
        List<Map<String, Object>> rows = db.query(
                "SELECT id, name, code, description FROM department WHERE name LIKE :kw OR code LIKE :kw", p);
        if (!rows.isEmpty()) {
            return JsonUtils.toJson(Map.of("count", rows.size(), "matched_by", "department_name", "departments", rows));
        }
        // 容错：按科室名查不到时，联动「症状→科室」映射表（模型常用症状词当关键词）
        List<Map<String, Object>> mapped = db.query(
                "SELECT d.id, d.name, d.code, d.description, m.symptom_keyword, m.weight "
                        + "FROM symptom_department_map m JOIN department d ON d.id=m.department_id "
                        + "WHERE m.symptom_keyword LIKE :kw ORDER BY m.weight DESC", p);
        if (!mapped.isEmpty()) {
            return JsonUtils.toJson(Map.of("count", mapped.size(), "matched_by", "symptom_map", "departments", mapped));
        }
        return JsonUtils.toJson(Map.of("count", 0, "matched_by", "none", "departments", List.of(),
                "hint", "未匹配到科室，请换关键词或先调 symptom_tag_search"));
    }

    private String doctorQuery(Long deptId, String title) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("dept", deptId);
        p.put("title", title);
        String sql = "SELECT id, name, department_id, title, specialty, good_at FROM doctor WHERE 1=1";
        if (deptId != null) sql += " AND department_id=:dept";
        if (title != null && !title.isBlank()) sql += " AND title=:title";
        List<Map<String, Object>> rows = db.query(sql, p);
        return JsonUtils.toJson(Map.of("count", rows.size(), "doctors", rows));
    }

    private String scheduleQuery(Long doctorId, String date, String period) {
        // period 归一化：模型常给 morning/afternoon/上午/下午，库里是 AM/PM
        String normPeriod = normalizePeriod(period);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("doc", doctorId);
        p.put("date", date);
        p.put("period", normPeriod);
        String sql = "SELECT id, doctor_id, department_id, work_date, period, total_num, remain_num, status "
                + "FROM doctor_schedule WHERE doctor_id=:doc AND work_date=:date AND status=1";
        if (normPeriod != null && !normPeriod.isBlank()) sql += " AND period=:period";
        List<Map<String, Object>> rows = db.query(sql, p);
        if (!rows.isEmpty()) {
            return JsonUtils.toJson(Map.of("count", rows.size(), "schedules", rows));
        }
        // 容错：指定日期查不到时，自动放宽到未来 7 天该医生的所有排班
        List<Map<String, Object>> wide = db.query(
                "SELECT id, doctor_id, department_id, work_date, period, total_num, remain_num, status "
                        + "FROM doctor_schedule WHERE doctor_id=:doc AND status=1 AND remain_num>0 "
                        + "AND work_date>=CURDATE() ORDER BY work_date, period LIMIT 10",
                Map.of("doc", doctorId));
        return JsonUtils.toJson(Map.of(
                "count", wide.size(), "schedules", wide,
                "note", "指定日期 " + date + (normPeriod == null || normPeriod.isBlank() ? "" : " " + normPeriod)
                        + " 无排班，已自动返回该医生未来 7 天可用号源"));
    }

    /** morning/AM/上午 → AM；afternoon/PM/下午 → PM；其余原样返回（null 安全）。 */
    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) return "";
        String s = period.trim().toUpperCase();
        if (s.equals("MORNING") || s.contains("上午") || s.equals("AM")) return "AM";
        if (s.equals("AFTERNOON") || s.contains("下午") || s.equals("PM")) return "PM";
        return period.trim();
    }

    private String symptomSearch(String kw) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kw", "%" + (kw == null ? "" : kw) + "%");
        List<Map<String, Object>> tags = db.query(
                "SELECT id, name, department_id, description FROM symptom_tag WHERE name LIKE :kw OR description LIKE :kw", p);
        List<Map<String, Object>> maps = db.query(
                "SELECT symptom_keyword, department_id, weight FROM symptom_department_map WHERE symptom_keyword LIKE :kw", p);
        return JsonUtils.toJson(Map.of("tags", tags, "deptHints", maps));
    }

    private String knowledgeRetrieve(String query, Long topK) {
        List<Map<String, Object>> hits = knowledge.retrieve(query, null, topK == null ? props.getRag().getTopK() : topK.intValue());
        List<Map<String, Object>> slim = new ArrayList<>();
        for (Map<String, Object> h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", h.get("content"));
            m.put("score", h.get("score"));
            m.put("source_title", h.get("source_title"));
            m.put("source_organization", h.get("source_organization"));
            m.put("source_version", h.get("source_version"));
            m.put("source_url", h.get("source_url"));
            m.put("source_doi", h.get("source_doi"));
            m.put("section_ref", h.get("section_ref"));
            m.put("citation_key", h.get("citation_key"));
            m.put("authority_level", h.get("authority_level"));
            m.put("review_status", h.get("review_status"));
            m.put("clinical_use", h.get("clinical_use"));
            m.put("source_notice", h.get("source_notice"));
            m.put("safety_note", h.get("safety_note"));
            slim.add(m);
        }
        return JsonUtils.toJson(Map.of("count", slim.size(), "segments", slim));
    }

    private void writeLog(String code, String input, String output, String status, int ms, Long runId, Long stepId, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", runId);
        m.put("step_id", stepId);
        m.put("tool_code", code);
        m.put("source", source == null ? "agent" : source);
        m.put("input_json", input);
        m.put("output_json", output.length() > 4000 ? output.substring(0, 4000) : output);
        m.put("status", status);
        m.put("cost_ms", ms);
        db.insert("INSERT INTO mcp_tool_call_log (run_id, step_id, tool_code, source, input_json, output_json, status, cost_ms) "
                + "VALUES (:run_id,:step_id,:tool_code,:source,:input_json,:output_json,:status,:cost_ms)", m);
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private Long num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
