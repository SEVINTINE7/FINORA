package lk.finora.app.ui.importer;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository.HttpFailure;
import lk.finora.app.data.repo.CsvImportRepository;
import lk.finora.app.data.repo.CsvImportRepository.Preview;
import lk.finora.app.data.repo.CsvImportRepository.Result;
import lk.finora.app.data.repo.CsvImportRepository.Row;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class CsvImportViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final CsvImportRepository repository = work.requests == null ? null
            : new CsvImportRepository(work.requests);
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Integer> selected = new HashSet<>();
    private byte[] file;
    private boolean reading;
    boolean needsPreview;
    boolean uncertain;
    Preview preview;
    Result result;

    boolean busy() { return reading || work.busy(); }
    int selectedCount() { return selected.size(); }
    boolean isSelected(Row row) { return selected.contains(row.line()); }
    boolean canRefresh() { return file != null && needsPreview; }

    void choose(ContentResolver resolver, Uri uri) {
        if (!work.isCurrent() || busy()) return;
        file = null;
        preview = null;
        result = null;
        uncertain = false;
        needsPreview = false;
        selected.clear();
        work.error = 0;
        reading = true;
        work.changed();
        reader.execute(() -> {
            byte[] content = null;
            int error = 0;
            try (var stream = resolver.openInputStream(uri)) {
                content = CsvImportFile.read(stream);
            } catch (CsvImportFile.TooLarge tooLarge) {
                error = R.string.csv_too_large;
            } catch (IOException | SecurityException invalid) {
                error = R.string.csv_file_invalid;
            }
            byte[] bytes = content;
            int message = error;
            main.post(() -> {
                if (!work.isCurrent()) return;
                reading = false;
                if (message != 0) {
                    work.error = message;
                    work.changed();
                } else {
                    file = bytes;
                    refresh();
                }
            });
        });
    }

    void refresh() {
        if (!work.isCurrent() || busy() || file == null) return;
        needsPreview = true;
        work.run(() -> repository.preview(file), CsvImportRepository::readPreview, loaded -> {
            preview = loaded;
            selected.clear();
            uncertain = false;
            needsPreview = false;
        }, false, failure -> {
            if (failure instanceof HttpFailure http) {
                if (http.status == 413) work.error = R.string.csv_too_large;
                if (http.status == 400) work.error = R.string.csv_preview_invalid;
            }
        });
    }

    void select(Row row, boolean checked) {
        if (!work.isCurrent() || busy() || needsPreview || !row.selectable()) return;
        if (checked) selected.add(row.line());
        else selected.remove(row.line());
    }

    void confirm(Preview reviewed, Set<Integer> reviewedLines) {
        if (!work.isCurrent() || busy() || needsPreview || preview != reviewed
                || selected.isEmpty() || !selected.equals(reviewedLines)) return;
        var rows = preview.rows().stream().filter(this::isSelected)
                .collect(java.util.stream.Collectors.toList());
        work.run(() -> repository.confirm(rows), response -> CsvImportRepository.readResult(response, rows.size()), saved -> {
            result = saved;
            file = null;
            preview = null;
            selected.clear();
        }, false, failure -> {
            needsPreview = true;
            uncertain = isUncertain(failure);
            if (!uncertain && failure instanceof HttpFailure http && http.status == 400) {
                work.error = R.string.csv_confirm_rejected;
            }
        });
    }

    Set<Integer> reviewedLines() { return new HashSet<>(selected); }

    static boolean isUncertain(Exception failure) {
        return !(failure instanceof HttpFailure http)
                || (http.status != 400 && http.status != 401 && http.status != 403);
    }

    private void clear() {
        reader.shutdownNow();
        file = null;
        preview = null;
        result = null;
        selected.clear();
        reading = false;
        main.removeCallbacksAndMessages(null);
    }

    @Override protected void onCleared() { work.close(); }
}
