package com.hospital.v2.boot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the human-handoff fields to an existing production database.
 *
 * The MySQL profile deliberately disables Spring's schema initializer. This
 * small idempotent migration keeps an existing CareNexus volume compatible
 * with the exception-routing workflow without replaying seed data.
 */
@Component
@Order(1)
public final class TriageWorkflowMigration implements ApplicationRunner {
    private final DataSource dataSource;

    public TriageWorkflowMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("triage_suggestion")) return;
        addColumnIfMissing("triage_suggestion", "manual_question", "VARCHAR(256)");
        addColumnIfMissing("triage_suggestion", "review_note", "VARCHAR(512)");
        addColumnIfMissing("triage_suggestion", "reviewer", "VARCHAR(64)");
        addColumnIfMissing("triage_suggestion", "review_time", "DATETIME");
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getTables(connection.getCatalog(), connection.getSchema(), null,
                    new String[]{"TABLE"})) {
                while (result.next()) {
                    if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(connection.getCatalog(), connection.getSchema(), null, null)) {
                while (result.next()) {
                    if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))
                            && column.equalsIgnoreCase(result.getString("COLUMN_NAME"))) return;
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }
}
