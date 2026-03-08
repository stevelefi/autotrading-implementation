package com.autotrading.services.performance;

import com.autotrading.services.performance.db.PositionEntity;
import com.autotrading.services.performance.db.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PositionRepository}.
 * Uses Mockito to verify correct SQL and parameter binding — consistent with
 * the project's pure-unit-test style.  SQL semantics are covered by e2e tests
 * running against a real PostgreSQL database.
 */
class PositionRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private PositionRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = new PositionRepository(jdbc);
    }

    private PositionEntity pos(String agentId, String instrumentId, int qty, String avgCost) {
        return new PositionEntity(agentId, instrumentId, qty,
                new BigDecimal(avgCost), BigDecimal.ZERO, Instant.now());
    }

    @Test
    void save_calls_update_with_upsert_sql() {
        repository.save(pos("agent-1", "AAPL", 10, "150.00"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());

        SqlParameterSource params = captor.getValue();
        assertThat(params.getValue("agentId")).isEqualTo("agent-1");
        assertThat(params.getValue("instrumentId")).isEqualTo("AAPL");
        assertThat(params.getValue("qty")).isEqualTo(10);
        assertThat((BigDecimal) params.getValue("avgCost"))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void save_sql_contains_on_conflict_upsert_clause() {
        repository.save(pos("agent-2", "TSLA", 5, "200.00"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), any(SqlParameterSource.class));

        String sql = sqlCaptor.getValue().toUpperCase();
        assertThat(sql).contains("ON CONFLICT");
        assertThat(sql).contains("DO UPDATE");
        assertThat(sql).contains("EXCLUDED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByAgentId_delegates_to_jdbc_query() {
        PositionEntity expected = pos("agent-3", "AAPL", 7, "100.00");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(expected));

        List<PositionEntity> result = repository.findByAgentId("agent-3");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo("agent-3");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByAgentIdAndInstrumentId_returns_empty_when_jdbc_returns_empty_list() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<PositionEntity> result =
                repository.findByAgentIdAndInstrumentId("missing", "AAPL");

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void backwardCompatAliases_delegate_to_primary_methods() {
        PositionEntity expected = pos("agent-4", "MSFT", 3, "300.00");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(expected));

        assertThat(repository.findByIdAgentId("agent-4")).hasSize(1);
        assertThat(repository.findByIdAgentIdAndIdInstrumentId("agent-4", "MSFT")).isPresent();
    }
}

