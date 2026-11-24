package com.hospital.v2.service;

import com.hospital.v2.config.*;
import com.hospital.v2.dao.Db;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.output.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AgentBudgetTest {
    @Autowired Db db;
    @Autowired PreTriageService sessions;
    private Response<AiMessage> reply(AiMessage message, boolean degraded) {
        return Response.from(message, new TokenUsage(0, 0), FinishReason.STOP,
                degraded ? Map.of("degraded", "true") : Map.of());
    }
    @Test void last_tool_gets_a_final_answer_without_repeating_ai_message() {
        var gateway = mock(LlmGateway.class);
        var tools = mock(ToolCenter.class);
        var props = new HospitalProps(); props.getLlm().setMaxAgentSteps(1);
        when(gateway.activePromptTemplate(anyString())).thenReturn("分诊");
        when(tools.buildSpecs()).thenReturn(List.of());
        when(tools.execute(anyString(), anyString(), anyLong(), anyLong(), anyString())).thenReturn("[]");
        var first = AiMessage.from(List.of(
                ToolExecutionRequest.builder().id("a").name("department_query").arguments("{}").build(),
                ToolExecutionRequest.builder().id("b").name("doctor_query").arguments("{}").build()));
        when(gateway.generate(anyList(), anyList())).thenReturn(reply(first, false),
                reply(AiMessage.from("{\"department_id\":2,\"doctor_id\":3,\"reason\":\"已核查\"}"), false));
        var result = new ReActTriageAgent(gateway, tools, props, db).triage(sessions.startSession(1L), 1L, "普通不适");
        assertFalse((Boolean) result.suggestion().get("needs_confirmation"));
        verify(gateway, times(2)).generate(argThat(messages -> messages.size() == 6), eq(List.of()));
    }
    @Test void malformed_and_degraded_output_requires_review() {
        var gateway = mock(LlmGateway.class); var tools = mock(ToolCenter.class);
        var props = new HospitalProps(); props.getLlm().setMaxAgentSteps(1);
        when(gateway.activePromptTemplate(anyString())).thenReturn("分诊");
        when(tools.buildSpecs()).thenReturn(List.of());
        when(gateway.generate(anyList(), anyList())).thenReturn(reply(AiMessage.from("{broken}"), true));
        var result = new ReActTriageAgent(gateway, tools, props, db).triage(sessions.startSession(1L), 1L, "普通不适");
        assertTrue((Boolean) result.suggestion().get("needs_confirmation"));
        assertFalse((Boolean) result.suggestion().get("can_register"));
    }
    @Test void provider_timeout_marks_failed_run_without_bookable_suggestion() {
        var gateway=mock(LlmGateway.class); var tools=mock(ToolCenter.class);
        var props=new HospitalProps(); props.getLlm().setMaxAgentSteps(1);
        when(gateway.activePromptTemplate(anyString())).thenReturn("分诊");
        when(tools.buildSpecs()).thenReturn(List.of());
        when(gateway.generate(anyList(),anyList())).thenThrow(new RuntimeException("injected provider timeout"));
        long session=sessions.startSession(1L);
        assertThrows(RuntimeException.class,()->new ReActTriageAgent(gateway,tools,props,db).triage(session,1L,"普通不适"));
        assertEquals("FAILED",db.queryOne("SELECT status FROM agent_run WHERE session_id=:id",Map.of("id",session)).get("status"));
        assertTrue(db.query("SELECT id FROM triage_suggestion WHERE session_id=:id",Map.of("id",session)).isEmpty());
        verify(gateway,times(1)).generate(anyList(),anyList());
    }
}
