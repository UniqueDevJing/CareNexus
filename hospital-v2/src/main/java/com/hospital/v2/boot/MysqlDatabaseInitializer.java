package com.hospital.v2.boot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Initializes a brand-new MySQL database before any startup task queries business tables.
 *
 * Production disables Spring's always-run SQL initializer so restarts do not replay seed
 * data. This one-shot guard keeps a fresh Compose volume deployable while refusing to
 * guess how to repair a partially migrated database.
 */
@Component
@Profile("mysql")
@Order(0)
public final class MysqlDatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public MysqlDatabaseInitializer(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean users = tableExists("user_account");
        boolean knowledge = tableExists("knowledge_segment");
        if (users && knowledge) return;
        if (users || knowledge) {
            throw new IllegalStateException(
                    "CareNexus MySQL schema is partially initialized; apply a versioned migration before starting");
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource("db/schema.sql"));
        populator.addScript(new ClassPathResource("db/data.sql"));
        populator.execute(dataSource);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                table);
        return count != null && count > 0;
    }
}
