package com.hospital.v2.boot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression test for legacy production knowledge documents missing provenance. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:knowledge_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class KnowledgeEvidenceMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired KnowledgeEvidenceMigration migration;

    @Test
    void backfills_legacy_document_source_without_overwriting_clinician_review() throws Exception {
        jdbc.update("INSERT INTO knowledge_doc (base_id, title, status, authority_level, review_status) "
                        + "VALUES (2, ?, 'READY', 'UNVERIFIED', 'PENDING_REVIEW')",
                "心房颤动与卒中预防");

        migration.run(new DefaultApplicationArguments());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_organization, source_url, source_version, review_status FROM knowledge_doc WHERE title=?",
                "心房颤动与卒中预防");
        assertEquals("AHA/ACC/ACCP/HRS", row.get("source_organization"));
        assertTrue(String.valueOf(row.get("source_url")).startsWith("https://www.heart.org/"));
        assertEquals("2023", row.get("source_version"));
        assertEquals("SOURCE_VERIFIED", row.get("review_status"));
    }
}
