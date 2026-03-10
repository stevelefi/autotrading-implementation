package com.autotrading.e2e;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

class FlywayMigrationTest {

  static EmbeddedPostgres pg;

  @BeforeAll
  static void startDb() throws Exception {
    pg = EmbeddedPostgres.start();
  }

  @AfterAll
  static void stopDb() throws Exception {
    if (pg != null) pg.close();
  }

  @Test
  void appliesBaselineMigrations() throws Exception {
    Flyway flyway = Flyway.configure()
        .dataSource(pg.getPostgresDatabase())
        .locations("filesystem:../../db/migrations")
        .load();

    int applied = flyway.migrate().migrationsExecuted;
    assertThat(applied).isGreaterThanOrEqualTo(1);

    try (Connection conn = pg.getPostgresDatabase().getConnection();
         ResultSet rs = conn.createStatement().executeQuery(
             "SELECT COUNT(*) FROM information_schema.tables"
             + " WHERE table_schema = 'public' AND table_name = 'outbox_events'")) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }

    // V2 added NEXT_RETRY_AT column for exponential back-off
    try (Connection conn = pg.getPostgresDatabase().getConnection();
         ResultSet rs = conn.createStatement().executeQuery(
             "SELECT COUNT(*) FROM information_schema.columns"
             + " WHERE table_schema = 'public'"
             + "   AND table_name = 'outbox_events'"
             + "   AND column_name = 'next_retry_at'")) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }

    // V9 added broker_health_status table
    try (Connection conn = pg.getPostgresDatabase().getConnection();
         ResultSet rs = conn.createStatement().executeQuery(
             "SELECT COUNT(*) FROM information_schema.tables"
             + " WHERE table_schema = 'public'"
             + "   AND table_name = 'broker_health_status'")) {
      rs.next();
      assertThat(rs.getInt(1)).as("broker_health_status table should exist after V9 migration").isEqualTo(1);
    }
  }
}
