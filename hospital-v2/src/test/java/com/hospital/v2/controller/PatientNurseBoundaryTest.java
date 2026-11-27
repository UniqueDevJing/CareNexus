package com.hospital.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP 层测试：验证患者/护士边界、预问诊输入兜底，以及公开健康检查。 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hospital_patient_nurse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class PatientNurseBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void health_is_public_but_role_workbenches_are_isolated() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        String patient = login("patient", "123456");
        String nurse = login("nurse", "123456");
        mvc.perform(get("/api/nurse/triage/queue").header("Authorization", "Bearer " + patient))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/nurse/triage/queue").header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patient/profile").header("Authorization", "Bearer " + nurse))
                .andExpect(status().isForbidden());
    }

    @Test
    void patient_triage_rejects_blank_and_overlong_complaints_before_agent_execution() throws Exception {
        String patient = login("patient", "123456");
        long sessionId = startSession(patient);
        mvc.perform(post("/api/patient/triage")
                        .header("Authorization", "Bearer " + patient)
                        .param("sessionId", String.valueOf(sessionId))
                        .param("complaint", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("不能为空")));

        mvc.perform(post("/api/patient/triage")
                        .header("Authorization", "Bearer " + patient)
                        .param("sessionId", String.valueOf(sessionId))
                        .param("complaint", "症状".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void registration_rejects_malformed_age_and_multibyte_bcrypt_overflow() throws Exception {
        String malformedAge = json.createObjectNode()
                .put("username", "age" + (System.nanoTime() % 100000000))
                .put("password", "valid-pass-123")
                .put("name", "审计测试")
                .put("age", "abc")
                .toString();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(malformedAge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("年龄必须")));

        String unicodePassword = json.createObjectNode()
                .put("username", "unicode" + (System.nanoTime() % 100000000))
                .put("password", "中".repeat(30))
                .put("name", "审计测试")
                .toString();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(unicodePassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("UTF-8")));
    }

    @Test
    void session_endpoint_resumes_server_memory_and_rejects_duplicate_force_new() throws Exception {
        String patient = login("patient", "123456");
        long first = startSession(patient);

        String resumedBody = mvc.perform(post("/api/patient/session")
                        .header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumed").value(true))
                .andReturn().getResponse().getContentAsString();
        long resumed = json.readTree(resumedBody).path("data").path("sessionId").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(first, resumed,
                "重复进入患者端必须恢复原会话，不能把智能体上下文清空");

        String currentBody = mvc.perform(get("/api/patient/session/current")
                        .header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(first,
                json.readTree(currentBody).path("data").path("session_id").asLong());

        String freshBody = mvc.perform(post("/api/patient/session")
                        .header("Authorization", "Bearer " + patient)
                        .param("forceNew", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumed").value(true))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(first,
                json.readTree(freshBody).path("data").path("sessionId").asLong());
    }

    @Test
    void a_locked_triage_session_is_not_reused_for_the_next_visit() throws Exception {
        String patient = login("patient", "123456");
        long first = startSession(patient);
        mvc.perform(post("/api/patient/triage")
                        .header("Authorization", "Bearer " + patient)
                        .param("sessionId", String.valueOf(first))
                        .param("complaint", "咳嗽一周，没有发热、呼吸困难和胸痛，想预约门诊"))
                .andExpect(status().isOk());

        String nextBody = mvc.perform(post("/api/patient/session")
                        .header("Authorization", "Bearer " + patient)
                        .param("forceNew", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumed").value(false))
                .andReturn().getResponse().getContentAsString();
        long next = json.readTree(nextBody).path("data").path("sessionId").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(first, next,
                "完成分诊后应开启新的就诊任务，不能把已锁定会话当作当前会话");
    }

    @Test
    void patient_cannot_use_another_patients_session() throws Exception {
        String firstPatient = login("patient", "123456");
        String secondPatient = login("huimin", "123456");
        long sessionId = startSession(firstPatient);
        mvc.perform(get("/api/patient/session/history")
                        .header("Authorization", "Bearer " + secondPatient)
                        .param("sessionId", String.valueOf(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("无权访问")));
    }

    @Test
    void login_response_keeps_sensitive_refresh_token_out_of_body_and_logout_clears_cookies() throws Exception {
        String body = json.createObjectNode().put("username", "patient").put("password", "123456").toString();
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = json.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        assertTrue(login.getResponse().getContentAsString().contains("hospital_refresh") == false);

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                        .anyMatch(cookie -> cookie.contains("hospital_session=") && cookie.contains("Max-Age=0"))));
    }

    private String login(String username, String password) throws Exception {
        String body = json.createObjectNode().put("username", username).put("password", password).toString();
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(response);
        return root.path("data").path("token").asText();
    }

    private long startSession(String token) throws Exception {
        String response = mvc.perform(post("/api/patient/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("sessionId").asLong();
    }
}
