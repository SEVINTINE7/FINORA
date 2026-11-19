package lk.finora.api.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    // Ownership check: only finds the row if it belongs to this user
    Optional<Consent> findByIdAndUserId(Long id, Long userId);

    // The user's newest consent that has not been withdrawn (empty = no active consent)
    Optional<Consent> findFirstByUserIdAndWithdrawnAtIsNullOrderByAcceptedAtDesc(Long userId);
}
