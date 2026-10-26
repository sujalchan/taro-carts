package nz.ac.aut.comp713.dispatcher_service.internal;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds the idempotency key without rebuilding or replacing existing SQLite rows. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProvisioningSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    ProvisioningSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            boolean columnExists = false;
            try (var columns = statement.executeQuery("PRAGMA table_info(dispatchers)")) {
                while (columns.next()) {
                    if ("provisioning_request_id".equals(columns.getString("name"))) columnExists = true;
                }
            }
            if (!columnExists) statement.executeUpdate("ALTER TABLE dispatchers ADD COLUMN provisioning_request_id bigint");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatchers_provisioning_request_id "
                    + "ON dispatchers(provisioning_request_id)");
        }
    }
}
