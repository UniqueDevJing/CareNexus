package com.hospital.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.v2.common.ProviderUnavailableException;
import com.hospital.v2.config.LlmGateway;
import com.hospital.v2.dao.Db;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 外部模型不可用时，HTTP 层必须返回可识别的人工接管状态，不能伪造分诊结果。 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hospital_provider_failure;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class ProviderFailureHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Db db;
    @MockBean LlmGateway gateway;

    @Test
    void triage_returns_503_and_creates_no_suggestion_when_provider_is_down() throws Exception {
        when(gateway.activePromptTemplate(anyString())).thenReturn("分诊");
        when(gateway.generate(anyList(), anyList())).thenThrow(
                new ProviderUnavailableException("模型服务暂不可用，请稍后重试或转人工服务", new RuntimeException("timeout")));

        String token = login();
        String sessionBody = mvc.perform(post("/api/patient/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long sessionId = json.readTree(sessionBody).path("data").path("sessionId").asLong();

        mvc.perform(post("/api/patient/triage")
                        .header("Authorization", "Bearer " + token)
                        .param("sessionId", String.valueOf(sessionId))
                        .param("complaint", "咳嗽一周，没有发热、呼吸困难和胸痛，想预约门诊"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg").value("模型服务暂不可用，请稍后重试或转人工服务"));

        org.junit.jupiter.api.Assertions.assertTrue(db.query(
                "SELECT id FROM triage_suggestion WHERE session_id=:session", java.util.Map.of("session", sessionId)).isEmpty());
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"patient\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(response);
        return root.path("data").path("token").asText();
    }
}
