package com.hospital.v2.service;

import com.hospital.v2.config.LlmGateway;
import com.hospital.v2.dao.Db;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * 智能预问诊：多轮 SSE 追问（简练、口语化），会话记忆落库；
 * 分诊后会话被锁定，不能再追问，避免建议与上下文发生漂移。
 */
@Service
public class PreTriageService {
    // 患者常用中文数字描述时长（如“三天”“两周”），不能只识别阿拉伯数字。
    private static final Pattern DURATION = Pattern.compile("((?:\\d+|[一二两三四五六七八九十百千万几数]+)\\s*(小时|天|周|月|年)|今天|昨天|刚刚|持续|反复|多年)");
    private static final Pattern SAFETY_CONTEXT = Pattern.compile("(没有|无|否|伴随|发热|咳痰|胸闷|胸痛|呼吸|喘|头晕|意识|出血|呕吐|腹泻|过敏|加重|严重)");
    private static final Pattern VISIT_GOAL = Pattern.compile("(预约|挂号|就诊|看病|门诊|医生|建议|咨询|检查)");
    private static final String PRE_SYS = "你是医院预问诊助手，用简短口语化中文追问患者症状，每次只问一个重点，不要罗列。"
            + "当症状、持续时间和重要伴随症状已经明确，或患者已经回答三轮时，不再重复追问，"
            + "必须以‘信息已齐全，正在为您匹配就诊安排…’结束回复。";

    private final Db db;
    private final LlmGateway llm;

    public PreTriageService(Db db, LlmGateway llm) {
        this.db = db;
        this.llm = llm;
    }

    public long startSession(Long patientId) {
        return startSession(patientId, null);
    }

    /** 创建与一次门诊任务绑定的对话；旧调用保留兼容，避免历史接口直接失效。 */
    public long startSession(Long patientId, Long episodeId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient_id", patientId == null ? 0 : patientId);
        m.put("episode_id", episodeId);
        return db.insert("INSERT INTO pre_triage_session (patient_id, episode_id, status) "
                + "VALUES (:patient_id,:episode_id,'ACTIVE')", m);
    }

    /**
     * 找回患者尚未结束的最近一次会话。
     * 会话恢复不能依赖浏览器 localStorage：换设备、清理缓存或重新登录后，
     * 服务端仍必须知道患者正在推进哪一次就诊任务，否则智能体会退化成每次从零开始。
     */
    public Map<String, Object> resumableSession(long patientId) {
        return db.queryOne("SELECT s.id AS session_id, s.episode_id, s.status AS session_status, "
                        + "s.locked, e.status AS episode_status "
                        + "FROM pre_triage_session s "
                        + "LEFT JOIN care_episode e ON e.id=s.episode_id "
                        + "WHERE s.patient_id=:patient "
                        + "AND (e.id IS NULL OR e.status NOT IN ('COMPLETED','CANCELLED')) "
                        + "ORDER BY s.update_time DESC, s.id DESC LIMIT 1",
                Map.of("patient", patientId));
    }

    public boolean isLocked(long sessionId) {
        Map<String, Object> row = db.queryOne("SELECT locked FROM pre_triage_session WHERE id=:id", Map.of("id", sessionId));
        return row != null && ((Number) row.get("locked")).intValue() == 1;
    }

    public void appendUser(long sessionId, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", sessionId);
        m.put("role", "user");
        m.put("content", text);
        db.insert("INSERT INTO pre_triage_message (session_id, role, content) VALUES (:session_id,:role,:content)", m);
        refreshMemorySummary(sessionId);
    }

    public void appendAssistant(long sessionId, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", sessionId);
        m.put("role", "assistant");
        m.put("content", text);
        db.insert("INSERT INTO pre_triage_message (session_id, role, content) VALUES (:session_id,:role,:content)", m);
        db.update("UPDATE pre_triage_session SET update_time=CURRENT_TIMESTAMP WHERE id=:id", Map.of("id", sessionId));
    }

    public boolean streamReply(long sessionId, String userText,
                               java.util.function.Consumer<String> onToken,
                               java.util.function.Consumer<String> onComplete,
                               java.util.function.Consumer<Throwable> onError) {
        if (isLocked(sessionId)) return false;
        return llm.streamText(promptFor(sessionId), conversation(sessionId, userText), onToken, text -> {
            appendAssistant(sessionId, text);
            onComplete.accept(text);
        }, onError);
    }

    /** 生成助手追问（真实模型或本地脚本）。 */
    public String generateReply(long sessionId, String userText) {
        if (isLocked(sessionId)) return "本次预问诊已结束并生成分诊建议，如需继续请咨询医生。";
        int turn = ((Number) db.queryOne(
                "SELECT COUNT(*) c FROM pre_triage_message WHERE session_id=:s AND role='user'",
                Map.of("s", sessionId)).get("c")).intValue();

        String reply;
        Readiness readiness = assessReadiness(sessionId, userText);
        if (!readiness.ready()) {
            reply = nextQuestion(readiness);
            appendAssistant(sessionId, reply);
            return reply;
        }
        String real = llm.chatText(promptFor(sessionId), conversation(sessionId, userText));
        if (real != null && !real.isBlank()) {
            reply = real;
        } else {
            reply = scripted(turn + 1);
        }
        appendAssistant(sessionId, reply);
        return reply;
    }

    /**
     * 分诊前的最小事实门槛。模型可以理解语言，但不能绕过必要事实检查直接给出预约方案。
     * 原始消息仍然完整保留，门槛只负责阻止“问不清就给方案”。
     */
    public Readiness assessReadiness(long sessionId, String supplemental) {
        List<String> statements = history(sessionId).stream()
                .filter(row -> "user".equalsIgnoreCase(String.valueOf(row.get("role"))))
                .map(row -> String.valueOf(row.getOrDefault("content", "")))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (supplemental != null && !supplemental.isBlank()
                && (statements.isEmpty() || !supplemental.equals(statements.get(statements.size() - 1)))) {
            statements.add(supplemental);
        }
        String all = String.join("，", statements);
        List<String> missing = new ArrayList<>();
        if (all.isBlank()) missing.add("主要不适");
        if (!DURATION.matcher(all).find()) missing.add("症状持续时间");
        if (!SAFETY_CONTEXT.matcher(all).find()) missing.add("是否伴随发热、呼吸困难、胸痛或其他加重表现");
        if (!VISIT_GOAL.matcher(all).find()) missing.add("本次想解决的就诊目标");
        return new Readiness(missing.isEmpty(), List.copyOf(missing));
    }

    private String nextQuestion(Readiness readiness) {
        if (readiness.missing().contains("主要不适")) return "先告诉我最困扰你的症状，身体哪里不舒服？";
        if (readiness.missing().contains("症状持续时间")) return "这个症状从什么时候开始？持续了多久，是一直有还是反复出现？";
        if (readiness.missing().contains("是否伴随发热、呼吸困难、胸痛或其他加重表现")) {
            return "有没有发热、呼吸困难、胸痛、意识异常等伴随情况？没有也请直接告诉我。";
        }
        return "你这次是想预约门诊、咨询就诊方向，还是先了解下一步检查？";
    }

    public record Readiness(boolean ready, List<String> missing) {}

    /** 分诊后锁定会话。 */
    public void lock(long sessionId, String summary) {
        db.update("UPDATE pre_triage_session SET status='LOCKED', locked=1, summary=:s WHERE id=:id",
                Map.of("s", summary == null ? "" : summary, "id", sessionId));
    }

    private String scripted(int t) {
        return switch (t) {
            case 1 -> "请问这个症状出现多久了？是持续性的还是一阵一阵的？";
            case 2 -> "有没有伴随其他不舒服，比如发热、咳痰、胸闷或者头晕？";
            case 3 -> "以前有没有类似的情况，或者正在长期吃哪些药？";
            default -> "信息已齐全，正在为您匹配就诊安排…";
        };
    }

    public List<Map<String, Object>> history(long sessionId) {
        return db.query("SELECT role, content, create_time FROM pre_triage_message WHERE session_id=:s ORDER BY id",
                Map.of("s", sessionId));
    }

    /** 返回落库的会话记忆摘要，供恢复接口和模型上下文共同使用。 */
    public String memorySummary(long sessionId) {
        Map<String, Object> row = db.queryOne("SELECT memory_summary FROM pre_triage_session WHERE id=:id",
                Map.of("id", sessionId));
        if (row == null) return "";
        String value = row.get("memory_summary") == null ? "" : String.valueOf(row.get("memory_summary"));
        if (value.isBlank()) {
            // 兼容升级前已存在的会话：首次恢复时从原始消息按需重建摘要。
            refreshMemorySummary(sessionId);
            row = db.queryOne("SELECT memory_summary FROM pre_triage_session WHERE id=:id", Map.of("id", sessionId));
            value = row == null || row.get("memory_summary") == null ? "" : String.valueOf(row.get("memory_summary"));
        }
        return value;
    }

    /**
     * 将患者跨轮次表达压缩为可恢复的事实摘要。
     * 原始消息仍完整保留；摘要只作为长上下文/换设备恢复时的索引，不替代原始证据。
     */
    private void refreshMemorySummary(long sessionId) {
        List<Map<String, Object>> rows = db.query(
                "SELECT content FROM pre_triage_message WHERE session_id=:s AND role='user' ORDER BY id",
                Map.of("s", sessionId));
        if (rows.isEmpty()) return;
        StringBuilder summary = new StringBuilder("本次就诊已记录：");
        int start = Math.max(0, rows.size() - 4);
        for (int i = start; i < rows.size(); i++) {
            String item = String.valueOf(rows.get(i).getOrDefault("content", "")).trim();
            if (item.isBlank()) continue;
            if (summary.length() > 10) summary.append("；");
            summary.append(item);
        }
        String value = summary.toString();
        if (value.length() > 1000) value = value.substring(value.length() - 1000);
        db.update("UPDATE pre_triage_session SET memory_summary=:memory, update_time=CURRENT_TIMESTAMP WHERE id=:id",
                Map.of("memory", value, "id", sessionId));
    }

    public String triageComplaint(long sessionId, String supplemental) {
        List<String> statements = history(sessionId).stream()
                .filter(row -> "user".equals(String.valueOf(row.get("role"))))
                .map(row -> String.valueOf(row.get("content")))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        // 前端可能传完整历史或尚未发送的补充；已有历史始终以服务端为准。
        String joined = String.join("，", statements);
        if (supplemental != null && !supplemental.isBlank()
                && !supplemental.equals(joined)
                && (statements.isEmpty() || !supplemental.equals(statements.get(statements.size() - 1)))) {
            statements.add(supplemental);
            // 未经聊天发送的补充也要进入服务端记忆，刷新或再次分诊时不能丢失。
            appendUser(sessionId, supplemental);
        }
        return String.join("，", statements);
    }

    /** 让模型看到同一会话的全部上下文，而不是只看到最后一句。 */
    private List<ChatMessage> conversation(long sessionId, String currentText) {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        List<Map<String, Object>> rows = history(sessionId);
        for (Map<String, Object> row : rows) {
            String content = String.valueOf(row.getOrDefault("content", ""));
            if ("assistant".equalsIgnoreCase(String.valueOf(row.get("role")))) {
                messages.add(AiMessage.from(content));
            } else {
                messages.add(UserMessage.from(content));
            }
        }
        // 兼容未来不经过 appendUser 的调用方，避免模型收到空上下文。
        if (messages.isEmpty() && currentText != null && !currentText.isBlank()) {
            messages.add(UserMessage.from("患者说：" + currentText));
        }
        return messages;
    }

    private String promptFor(long sessionId) {
        Map<String, Object> session = db.queryOne(
                "SELECT patient_id, memory_summary FROM pre_triage_session WHERE id=:id", Map.of("id", sessionId));
        StringBuilder prompt = new StringBuilder(PRE_SYS)
                .append("\n你必须结合本次会话中的历史消息继续追问，不能重复已经回答过的问题。")
                .append("如果患者表达了预约/挂号意图，先完成必要的症状与安全确认；信息齐全后由系统自动查询真实号源，")
                .append("不要要求患者点击生成、刷新或重新分诊，也不要声称已经替患者预约。")
                .append("在持续时间、伴随/风险表现和就诊目标任一缺失时，绝不能说信息已齐全或给出科室/号源方案，只追问缺失项。\n");
        String memory = session == null || session.get("memory_summary") == null
                ? "" : String.valueOf(session.get("memory_summary"));
        if (!memory.isBlank()) {
            prompt.append("已落库的本次就诊记忆（必须继续使用，不能当作新患者）：")
                    .append(memory).append("\n");
        }
        if (session != null && session.get("patient_id") != null) {
            Map<String, Object> profile = db.queryOne(
                    "SELECT name, gender, age, allergic_history, past_history FROM patient_profile WHERE id=:id",
                    Map.of("id", session.get("patient_id")));
            if (profile != null) {
                prompt.append("患者健康档案（仅用于本次安全判断）：")
                        .append(profile).append("\n");
            }
        }
        return prompt.toString();
    }
}
