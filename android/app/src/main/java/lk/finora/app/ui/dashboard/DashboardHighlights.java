package lk.finora.app.ui.dashboard;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import lk.finora.app.data.api.BillDto;
import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.BillRepository;
import lk.finora.app.data.repo.BudgetRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.data.repo.InsightsRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Call;

final class DashboardHighlights {
    final Section<InsightsRepository.Score> score = new Section<>();
    final Section<List<BudgetDto>> budgets = new Section<>();
    final Section<List<CategoryDto>> categories = new Section<>();
    final Section<List<BillDto>> bills = new Section<>();
    final Section<List<TransactionRepository.Row>> recent = new Section<>();
    final Section<List<GoalDto>> goals = new Section<>();

    void load(YearMonth month) {
        if (!score.work.isCurrent()) return;
        var requests = score.work.requests;
        var insights = new InsightsRepository(requests);
        var budgetRepository = new BudgetRepository(requests);
        var categoryRepository = new CategoryRepository(requests);
        var billRepository = new BillRepository(requests);
        var transactions = new TransactionRepository(requests);
        var goalRepository = new GoalRepository(requests);
        String period = month.toString();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        score.load(() -> insights.score(month), response -> {
            var value = AccountRepository.read(response, InsightsRepository.Score.class);
            if (!period.equals(value.month()) || !"D20-v1".equals(value.modelVersion())
                    || value.total() != null && (value.total() < 0 || value.total() > 100)) {
                throw new IOException("Invalid dashboard score");
            }
            return value;
        });
        budgets.load(() -> budgetRepository.list(period), BudgetRepository::rows);
        categories.load(categoryRepository::list, CategoryRepository::rows);
        bills.load(() -> billRepository.list(period), response -> upcoming(BillRepository.rows(response), today));
        var filter = new TransactionRepository.Filters(month.atDay(1).toString(),
                month.atEndOfMonth().toString(), null, null, null);
        recent.load(() -> transactions.list(filter, 0), response -> {
            var rows = TransactionRepository.readPage(response).items();
            for (var row : rows) {
                if (row == null || row.amount() == null || row.amount().signum() < 0
                        || row.txnDate() == null || !YearMonth.from(LocalDate.parse(row.txnDate())).equals(month)
                        || !("INCOME".equals(row.type()) || "EXPENSE".equals(row.type()))) {
                    throw new IOException("Invalid dashboard transaction");
                }
            }
            return rows.stream().limit(5).collect(java.util.stream.Collectors.toList());
        });
        goals.load(goalRepository::list, GoalRepository::rows);
    }

    static List<BillDto> upcoming(List<BillDto> rows, LocalDate today) {
        return rows.stream().filter(bill -> !bill.paid() && !LocalDate.parse(bill.dueDate()).isBefore(today))
                .sorted(Comparator.comparing(BillDto::dueDate).thenComparingLong(BillDto::id))
                .limit(3).collect(java.util.stream.Collectors.toList());
    }

    static GoalDto focus(List<GoalDto> rows) {
        return rows.stream().filter(goal -> "ACTIVE".equals(goal.status()))
                .min(Comparator.comparing(GoalDto::targetDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(GoalDto::id)).orElse(null);
    }

    void close() {
        for (var section : List.of(score, budgets, categories, bills, recent, goals)) section.work.close();
    }

    static final class Section<T> {
        final Work work = new Work(() -> {
            value = null;
            queued = null;
        });
        T value;
        private Runnable queued;

        void load(Supplier<Call> call, Work.Decoder<T> decode) {
            if (!work.isCurrent()) return;
            value = null;
            if (work.busy()) {
                queued = () -> load(call, decode);
                return;
            }
            work.run(call, decode, result -> {
                if (!runQueued()) value = result;
            }, false, ignored -> runQueued());
        }

        private boolean runQueued() {
            Runnable next = queued;
            queued = null;
            if (next == null) return false;
            next.run();
            return true;
        }
    }
}
