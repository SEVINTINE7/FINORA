package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryKind;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.goal.GoalService;
import lk.finora.api.goal.GoalStatus;
import lk.finora.api.goal.dto.GoalResponse;
import lk.finora.api.planning.dto.SimulationRequest;
import lk.finora.api.transaction.TransactionRepository;

class SimulationServiceTest {

    private final GoalService goals = mock(GoalService.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final TransactionRepository transactions =
            mock(TransactionRepository.class);

    private final SimulationService service =
            new SimulationService(goals, categories, transactions);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private void ownedGoal() {
        when(goals.get(42L, 7L)).thenReturn(new GoalResponse(
                7L, "Laptop", money("10000.00"), null,
                GoalStatus.ACTIVE, money("0.00"),
                money("10000.00"), 0));
    }

    private void visibleCategory(Long id, CategoryKind kind) {
        Category category = new Category();
        category.setKind(kind);

        when(categories.findVisibleById(id, 42L))
                .thenReturn(Optional.of(category));
    }

    private void spending(Long id, String amount) {
        when(transactions.sumExpenseForCategory(
                eq(42L), eq(id),
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(money(amount));
    }

    private static SimulationRequest.CategoryCut cut(
            long categoryId, String amount) {
        return new SimulationRequest.CategoryCut(
                categoryId, money(amount));
    }

    @Test
    void validCategoryCutsProduceCorrectMonthlyAmount() {
        ownedGoal();

        visibleCategory(8L, CategoryKind.EXPENSE);
        visibleCategory(9L, CategoryKind.EXPENSE);

        spending(8L, "600.00");
        spending(9L, "700.00");

        var result = service.simulate(42L, new SimulationRequest(
                7L,
                money("1500.00"),
                money("-100.00"),
                List.of(cut(8L, "200.00"),
                        cut(9L, "300.00"))));

        assertThat(result.categoryReductions())
                .isEqualByComparingTo("500.00");
        assertThat(result.hypotheticalMonthlySavings())
                .isEqualByComparingTo("1900.00");
        assertThat(result.monthsRequired()).isEqualTo(6);
        assertThat(result.status()).isEqualTo("POSSIBLE");
        assertThat(result.hypotheticalFinishDate()).isNotNull();

        YearMonth reference =
                YearMonth.parse(result.spendingReferenceMonth());

        verify(goals).get(42L, 7L);
        verify(categories).findVisibleById(8L, 42L);
        verify(categories).findVisibleById(9L, 42L);

        verify(transactions).sumExpenseForCategory(
                42L, 8L,
                reference.atDay(1), reference.atEndOfMonth());

        verify(transactions).sumExpenseForCategory(
                42L, 9L,
                reference.atDay(1), reference.atEndOfMonth());

        // No additional repository interactions or write calls.
        verifyNoMoreInteractions(goals, categories, transactions);
    }

    @Test
    void duplicateCategoryCutsAreRejected() {
        ownedGoal();
        visibleCategory(8L, CategoryKind.EXPENSE);
        spending(8L, "500.00");

        var request = new SimulationRequest(
                7L, money("1000.00"), money("0.00"),
                List.of(cut(8L, "100.00"),
                        cut(8L, "100.00")));

        assertThatThrownBy(() -> service.simulate(42L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(
                        "same category cannot be reduced twice");
    }

    @Test
    void reductionExceedingRecordedExpensesIsRejected() {
        ownedGoal();
        visibleCategory(8L, CategoryKind.EXPENSE);
        spending(8L, "200.00");

        var request = new SimulationRequest(
                7L, money("1000.00"), money("0.00"),
                List.of(cut(8L, "200.01")));

        assertThatThrownBy(() -> service.simulate(42L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exceeds recorded spending");
    }

    @Test
    void incomeCategoryCannotBeReduced() {
        ownedGoal();
        visibleCategory(8L, CategoryKind.INCOME);

        var request = new SimulationRequest(
                7L, money("1000.00"), money("0.00"),
                List.of(cut(8L, "100.00")));

        assertThatThrownBy(() -> service.simulate(42L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(
                        "Only expense categories can be reduced");

        verifyNoInteractions(transactions);
    }

    @Test
    void inaccessibleCategoryIsNotDisclosed() {
        ownedGoal();

        when(categories.findVisibleById(99L, 42L))
                .thenReturn(Optional.empty());

        var request = new SimulationRequest(
                7L, money("1000.00"), money("0.00"),
                List.of(cut(99L, "100.00")));

        assertThatThrownBy(() -> service.simulate(42L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Category not found");

        verifyNoInteractions(transactions);
    }

    @Test
    void nullRequestIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.simulate(42L, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Simulation inputs");

        verifyNoInteractions(goals, categories, transactions);
    }

    @Test
    void nullCategoryCutIsRejected() {
        ownedGoal();

        var request = new SimulationRequest(
                7L, money("1000.00"), money("0.00"),
                java.util.Collections.singletonList(null));

        assertThatThrownBy(() -> service.simulate(42L, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid category cut");

        verifyNoInteractions(categories, transactions);
    }
}