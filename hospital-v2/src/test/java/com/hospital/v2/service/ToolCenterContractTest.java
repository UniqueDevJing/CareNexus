package com.hospital.v2.service;

import com.hospital.v2.dao.Db;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工具参数解析失败时必须拒绝执行并写入失败审计，不能把空参数当成合法查询。 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:hospital_tool_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class ToolCenterContractTest {
    @Autowired ToolCenter tools;
    @Autowired Db db;

    @Test
    void malformed_arguments_fail_closed_and_are_audited() {
        String result = tools.execute("department_query", "{broken", null, null, "manual");
        assertTrue(result.contains("有效 JSON"));
        Map<String, Object> log = db.queryOne(
                "SELECT status,output_json FROM mcp_tool_call_log ORDER BY id DESC LIMIT 1", Map.of());
        assertEquals("FAILED", log.get("status"));
        assertTrue(String.valueOf(log.get("output_json")).contains("有效 JSON"));
    }
}
