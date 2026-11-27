package com.hospital.v2.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 为已有 MySQL 安装补齐门诊任务表。
 * 生产 profile 不执行 schema.sql，因此新版本必须具备幂等的启动迁移；所有语句均为 CREATE IF NOT EXISTS。
 */
@Component
@Order(3)
public final class CareEpisodeSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public CareEpisodeSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
    }

    private void ensureSchema() {
        ensureEpisodeColumn();
        ensureMemoryColumn();
        jdbc.execute("CREATE TABLE IF NOT EXISTS care_episode ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, patient_id BIGINT NOT NULL, "
                + "episode_type VARCHAR(24) NOT NULL DEFAULT 'OUTPATIENT', "
                + "status VARCHAR(32) NOT NULL DEFAULT 'INTAKE', current_action VARCHAR(64), "
                + "summary VARCHAR(1000), version INT NOT NULL DEFAULT 0, "
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, close_time DATETIME, "
                + "PRIMARY KEY (id), KEY idx_episode_patient_status (patient_id, status), KEY idx_episode_update (update_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS care_episode_event ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, episode_id BIGINT NOT NULL, event_type VARCHAR(48) NOT NULL, "
                + "from_status VARCHAR(32), to_status VARCHAR(32), actor_role VARCHAR(24) NOT NULL DEFAULT 'SYSTEM', "
                + "actor_id BIGINT, payload_json TEXT, idempotency_key VARCHAR(128), "
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), "
                + "UNIQUE KEY uk_episode_event_idempotency (episode_id, idempotency_key), "
                + "KEY idx_episode_event (episode_id, create_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_task ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, episode_id BIGINT NOT NULL, task_type VARCHAR(48) NOT NULL, "
                + "status VARCHAR(24) NOT NULL DEFAULT 'OPEN', priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL', "
                + "title VARCHAR(128) NOT NULL, detail VARCHAR(1000), assigned_role VARCHAR(24), assigned_id BIGINT, due_time DATETIME, "
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, complete_time DATETIME, PRIMARY KEY (id), "
                + "KEY idx_agent_task_episode (episode_id, status), KEY idx_agent_task_role (assigned_role, status)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        try {
            jdbc.execute("ALTER TABLE agent_task ADD COLUMN assigned_id BIGINT");
        } catch (org.springframework.jdbc.BadSqlGrammarException alreadyExists) {
            // Existing installations already contain the column.
        }
    }

    private void ensureEpisodeColumn() {
        try {
            jdbc.execute("ALTER TABLE pre_triage_session ADD COLUMN episode_id BIGINT");
        } catch (org.springframework.jdbc.BadSqlGrammarException alreadyExists) {
            // MySQL/H2 both report a grammar error for an already existing column; this is the expected idempotent path.
        }
    }

    private void ensureMemoryColumn() {
        try {
            jdbc.execute("ALTER TABLE pre_triage_session ADD COLUMN memory_summary VARCHAR(1000)");
        } catch (org.springframework.jdbc.BadSqlGrammarException alreadyExists) {
            // Existing installations already contain the column.
        }
    }
}
