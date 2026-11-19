package lk.finora.api.bill;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.bill.dto.BillRequest;
import lk.finora.api.bill.dto.BillResponse;
import lk.finora.api.bill.dto.BillSummaryResponse;
import lk.finora.api.security.CurrentUser;

/** The logged-in user's monthly bills. USER role only (T06 rules). Months are YYYY-MM. */
@RestController
@RequestMapping("/api/v1/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    /** All bills for one month with due date and paid flag. */
    @GetMapping
    public List<BillMonthResponse> list(@RequestParam String month) {
        return billService.listForMonth(CurrentUser.id(), month);
    }

    @GetMapping("/summary")
    public BillSummaryResponse summary(@RequestParam String month) {
        return billService.summary(CurrentUser.id(), month);
    }

    @GetMapping("/{id}")
    public BillResponse get(@PathVariable Long id) {
        return billService.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse create(@Valid @RequestBody BillRequest request) {
        return billService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public BillResponse update(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        return billService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        billService.delete(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPaid(@PathVariable Long id, @RequestParam String month) {
        billService.markPaid(CurrentUser.id(), id, month);
    }

    @DeleteMapping("/{id}/paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markUnpaid(@PathVariable Long id, @RequestParam String month) {
        billService.markUnpaid(CurrentUser.id(), id, month);
    }
}
