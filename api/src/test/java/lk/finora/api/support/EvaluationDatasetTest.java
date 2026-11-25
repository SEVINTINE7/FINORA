package lk.finora.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class EvaluationDatasetTest {
    @Test
    void blueprintHasStableContentAndHandCalculatedTotalsAcrossTwelveMonths() {
        var rows = EvaluationDataset.entries();
        assertThat(rows).isEqualTo(EvaluationDataset.entries()).hasSize(826);
        assertThat(rows.stream().map(EvaluationDataset.Entry::date).min(LocalDate::compareTo).orElseThrow()).isEqualTo("2025-12-01");
        assertThat(rows.stream().map(EvaluationDataset.Entry::date).max(LocalDate::compareTo).orElseThrow()).isEqualTo("2026-11-30");
        assertThat(rows.stream().map(e -> YearMonth.from(e.date())).distinct().count()).isEqualTo(12);
        assertThat(rows).allSatisfy(e -> {
            assertThat(e.amount()).isPositive(); assertThat(e.amount().scale()).isEqualTo(2);
        });
        assertThat(rows.stream().filter(e -> e.profile().equals("worker")).count()).isEqualTo(424);
        assertThat(rows.stream().filter(e -> e.category() == null).count()).isEqualTo(1);
        assertThat(sum("worker", "INCOME")).isEqualByComparingTo("720000.00");
        assertThat(sum("worker", "EXPENSE")).isEqualByComparingTo("506099.99");
        assertThat(sum("irregular", "INCOME")).isEqualByComparingTo("235250.00");
        assertThat(sum("irregular", "EXPENSE")).isEqualByComparingTo("117099.99");
        assertThat(EvaluationDataset.transactionDigest()).isEqualTo(EvaluationDataset.transactionDigest()).matches("[0-9a-f]{64}");
        System.out.println("T55 synthetic transaction SHA-256=" + EvaluationDataset.transactionDigest());
    }

    private BigDecimal sum(String profile, String type) {
        return EvaluationDataset.entries().stream().filter(e -> e.profile().equals(profile) && e.type().equals(type))
                .map(EvaluationDataset.Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsafeTargetsFailBeforeAnySeedWrite() throws Exception {
        for (String[] target : new String[][] {
                {"jdbc:mysql://127.0.0.1:3307/finora", "finora"},
                {"jdbc:mysql://db.example.invalid/finora_test", "finora_test"},
                {"jdbc:mysql://127.0.0.1/finora_test_shared", "finora_test_shared"},
                {"jdbc:mysql://localhost/finora_test", "finora"}})
            assertThatThrownBy(() -> EvaluationDatasetSeeder.validateTarget(target[0], target[1], true, false))
                    .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EvaluationDatasetSeeder.validateTarget("jdbc:mysql://localhost/finora_test", "finora_test", false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EvaluationDatasetSeeder.validateTarget("jdbc:mysql://localhost/finora_test", "finora_test", true, true))
                .isInstanceOf(IllegalStateException.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class); DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://production.example.invalid/finora");
        when(connection.getCatalog()).thenReturn("finora");
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(call -> ((ConnectionCallback<?>) call.getArgument(0)).doInConnection(connection));
        assertThatThrownBy(() -> new EvaluationDatasetSeeder(jdbc).seed()).isInstanceOf(IllegalStateException.class);
        verify(jdbc).execute(any(ConnectionCallback.class)); verifyNoMoreInteractions(jdbc);
    }
}
