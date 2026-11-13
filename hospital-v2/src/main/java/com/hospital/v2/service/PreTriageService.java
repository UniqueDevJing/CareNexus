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

/**
 * 智能预问诊：多轮 SSE 追问（简练、口语化），会话记忆落库；
 * 分诊后会话被锁定，不能再追问，避免建议与上下文发生漂移。
 */
@Service
public class PreTriageService {
    private static final String PRE_SYS = "你是医院预问诊助手，用简短口语化中文追问患者症状，每次只问一个重点，不要罗列。";

    private final Db db;
    private final LlmGateway llm;

    public PreTriageService(Db db, LlmGateway llm) {
        this.db = db;
        this.llm = llm;
    }

    public long startSession(Long patientId) {
        Map<String, Object> m = Map.of("patient_id", patientId == null ? 0 : patientId);
        return db.insert("INSERT INTO pre_triage_session (patient_id, status) VALUES (:patient_id,'ACTIVE')", m);
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
        String real = llm.chatText(promptFor(sessionId), conversation(sessionId, userText));
        if (real != null && !real.isBlank()) {
            reply = real;
        } else {
            reply = scripted(turn + 1);
        }
        appendAssistant(sessionId, reply);
        return reply;
    }

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
            default -> "了解得差不多了，正在为您发起智能分诊，请稍候…";
        };
    }

    public List<Map<String, Object>> history(long sessionId) {
        return db.query("SELECT role, content, create_time FROM pre_triage_message WHERE session_id=:s ORDER BY id",
                Map.of("s", sessionId));
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
                "SELECT patient_id FROM pre_triage_session WHERE id=:id", Map.of("id", sessionId));
        StringBuilder prompt = new StringBuilder(PRE_SYS)
                .append("\n你必须结合本次会话中的历史消息继续追问，不能重复已经回答过的问题。")
                .append("如果患者表达了预约/挂号意图，先完成必要的症状与安全确认，再明确告知患者点击‘生成建议’查看可预约号源；不要声称已经替患者预约。\n");
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
