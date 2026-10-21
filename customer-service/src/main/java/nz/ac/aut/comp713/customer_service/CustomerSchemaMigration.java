package nz.ac.aut.comp713.customer_service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds the account ID uniqueness rule after Hibernate adds the column to older SQLite databases. */
@Component
public class CustomerSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public CustomerSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_account_id ON customers(account_id)");
    }
}
