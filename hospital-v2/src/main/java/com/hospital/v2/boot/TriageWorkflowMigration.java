package com.hospital.v2.boot;

import com.hospital.v2.security.SensitiveDataCrypto;
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
 * Adds the human-handoff and billing fields to an existing production database.
 *
 * The MySQL profile deliberately disables Spring's schema initializer. This
 * small idempotent migration keeps an existing CareNexus volume compatible
 * with the exception-routing workflow without replaying seed data.
 */
@Component
@Order(1)
public final class TriageWorkflowMigration implements ApplicationRunner {
    private final DataSource dataSource;
    private final SensitiveDataCrypto crypto;

    public TriageWorkflowMigration(DataSource dataSource, SensitiveDataCrypto crypto) {
        this.dataSource = dataSource;
        this.crypto = crypto;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("triage_suggestion")) return;
        addColumnIfMissing("triage_suggestion", "manual_question", "VARCHAR(256)");
        addColumnIfMissing("triage_suggestion", "review_note", "VARCHAR(512)");
        addColumnIfMissing("triage_suggestion", "reviewer", "VARCHAR(64)");
        addColumnIfMissing("triage_suggestion", "review_time", "DATETIME");
        addColumnIfMissing("doctor", "registration_fee", "DECIMAL(10,2) NOT NULL DEFAULT 30.00");
        addColumnIfMissing("patient_profile", "phone", "VARCHAR(20)");
        addColumnIfMissing("patient_profile", "balance", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        addColumnIfMissing("patient_profile", "id_card_hash", "VARCHAR(64)");
        addColumnIfMissing("registration", "payment_status", "VARCHAR(16) NOT NULL DEFAULT 'UNPAID'");
        resizeSensitiveColumns();
        validateScheduleData();
        createBillingTables();
        createClinicalOrderTables();
        encryptLegacySensitiveData();
    }

    private void validateScheduleData() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM doctor_schedule "
                     + "WHERE total_num<=0 OR remain_num<0 OR remain_num>total_num")) {
            rows.next();
            if (rows.getLong(1) > 0) {
                throw new SQLException("doctor_schedule contains invalid total_num/remain_num values; repair data before startup");
            }
        }
    }

    private void resizeSensitiveColumns() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("h2");
            String alter = h2 ? "ALTER TABLE %s ALTER COLUMN %s VARCHAR(96)" : "ALTER TABLE %s MODIFY COLUMN %s VARCHAR(96)";
            for (String[] column : new String[][]{{"user_account", "phone"}, {"user_account", "id_card"},
                    {"patient_profile", "phone"}, {"patient_profile", "id_card"}}) {
                try { statement.execute(String.format(alter, column[0], column[1])); }
                catch (SQLException ignored) { /* column may already be wide enough or legacy DDL differs */ }
            }
        }
    }

    private void encryptLegacySensitiveData() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            encryptTable(connection, "user_account");
            encryptTable(connection, "patient_profile");
        }
    }

    private void encryptTable(Connection connection, String table) throws SQLException {
        try (var rows = connection.createStatement().executeQuery(
                "SELECT id, phone, id_card FROM " + table + " WHERE "
                        + "(phone IS NOT NULL AND phone <> '' AND phone NOT LIKE 'v1:%') OR "
                        + "(id_card IS NOT NULL AND id_card <> '' AND id_card NOT LIKE 'v1:%')")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String phone = rows.getString("phone");
                String idCard = rows.getString("id_card");
                try (var update = connection.prepareStatement("UPDATE " + table
                        + " SET phone=?, id_card=? WHERE id=?")) {
                    update.setString(1, crypto.encrypt(phone));
                    update.setString(2, crypto.encrypt(idCard));
                    update.setLong(3, id);
                    update.executeUpdate();
                }
            }
        }
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

    private void createBillingTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, patient_id BIGINT NOT NULL, "
                    + "bill_type VARCHAR(32) NOT NULL DEFAULT 'REGISTRATION', source_id BIGINT NULL, "
                    + "amount DECIMAL(10,2) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'UNPAID', "
                    + "description VARCHAR(512), paid_at DATETIME NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE KEY uk_bill_source (bill_type, source_id), KEY idx_bill_patient (patient_id), KEY idx_bill_status (status))");
            statement.execute("CREATE TABLE IF NOT EXISTS payment_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, "
                    + "transaction_type VARCHAR(16) NOT NULL DEFAULT 'PAYMENT', amount DECIMAL(10,2) NOT NULL, "
                    + "channel VARCHAR(32) NOT NULL DEFAULT 'CASH', trade_no VARCHAR(128), status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS', "
                    + "refund_reason VARCHAR(256), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "KEY idx_payment_bill (bill_id), KEY idx_payment_patient (patient_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS balance_ledger ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, patient_id BIGINT NOT NULL, change_type VARCHAR(32) NOT NULL, "
                    + "amount DECIMAL(10,2) NOT NULL, balance_after DECIMAL(10,2) NOT NULL, ref_bill_id BIGINT NULL, "
                    + "remark VARCHAR(256), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "KEY idx_balance_patient (patient_id), KEY idx_balance_created (created_at))");
        }
    }

    private void createClinicalOrderTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS exam_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, registration_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, "
                    + "doctor_id BIGINT NOT NULL, item_code VARCHAR(32), item_name VARCHAR(128) NOT NULL, fee DECIMAL(10,2) NOT NULL, "
                    + "status VARCHAR(24) NOT NULL DEFAULT 'ORDERED', result_text TEXT, performed_by BIGINT, result_time DATETIME, "
                    + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "KEY idx_exam_patient (patient_id), KEY idx_exam_registration (registration_id), KEY idx_exam_status (status))");
            statement.execute("CREATE TABLE IF NOT EXISTS prescription ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, registration_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL, "
                    + "status VARCHAR(24) NOT NULL DEFAULT 'ISSUED', total_amount DECIMAL(10,2) NOT NULL, note VARCHAR(512), "
                    + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "KEY idx_rx_patient (patient_id), KEY idx_rx_registration (registration_id), KEY idx_rx_status (status))");
            statement.execute("CREATE TABLE IF NOT EXISTS prescription_item ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prescription_id BIGINT NOT NULL, drug_code VARCHAR(64), drug_name VARCHAR(128) NOT NULL, "
                    + "dosage VARCHAR(64) NOT NULL, frequency VARCHAR(64) NOT NULL, days INT NOT NULL, quantity DECIMAL(10,2) NOT NULL, "
                    + "unit_price DECIMAL(10,2) NOT NULL, amount DECIMAL(10,2) NOT NULL, dispense_status VARCHAR(16) NOT NULL DEFAULT 'PENDING', "
                    + "KEY idx_rx_item_prescription (prescription_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS pharmacy_dispense ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prescription_id BIGINT NOT NULL, item_id BIGINT NOT NULL, pharmacist_id BIGINT, "
                    + "quantity DECIMAL(10,2) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS', dispense_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE KEY uk_dispense_item (item_id), KEY idx_dispense_rx (prescription_id))");
        }
    }
}
