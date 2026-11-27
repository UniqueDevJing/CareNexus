package com.hospital.v2.boot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean seedDemoData;

    public MysqlDatabaseInitializer(JdbcTemplate jdbc, DataSource dataSource,
                                    @Value("${hospital.seed-demo-data:false}") boolean seedDemoData) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean users = tableExists("user_account");
        boolean knowledge = tableExists("knowledge_segment");
        if (users && knowledge) {
            // A schema-only restore is not a usable installation: later migrations need
            // reference rows and would otherwise fail with an opaque constraint error.
            // Fail closed here with an operator-actionable message instead.
            if (hasRows("user_account") && hasRows("department")
                    && hasRows("doctor") && hasRows("knowledge_segment")) {
                return;
            }
            throw new IllegalStateException(
                    "CareNexus MySQL schema exists but reference data is incomplete; "
                            + "restore an approved backup or run the versioned data provisioning job before starting");
        }
        if (users || knowledge) {
            throw new IllegalStateException(
                    "CareNexus MySQL schema is partially initialized; apply a versioned migration before starting");
        }
        if (!seedDemoData) {
            throw new IllegalStateException(
                    "CareNexus MySQL database is empty; provision approved hospital data before starting "
                            + "or set HOSPITAL_SEED_DEMO_DATA=true only for an isolated demo environment");
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

    private boolean hasRows(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count != null && count > 0;
    }
}
