package lk.finora.api.bill;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    boolean existsByBillIdAndMonth(Long billId, String month);

    Optional<BillPayment> findByBillIdAndMonth(Long billId, String month);
}
