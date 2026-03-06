package com.autotrading.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

  @Test
  void appliesBaselineMigrations() throws Exception {
    String url = "jdbc:h2:mem:autotrading;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE\\;";

    Flyway flyway = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("filesystem:../../db/migrations")
        .load();

    int applied = flyway.migrate().migrationsExecuted;
    assertThat(applied).isGreaterThanOrEqualTo(1);

    try (Connection conn = DriverManager.getConnection(url, "sa", "");
         ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='OUTBOX_EVENTS'")) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }
}
