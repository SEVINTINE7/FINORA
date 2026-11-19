package lk.finora.api.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // What a user can see: active defaults (userId null) plus their own categories
    @Query("SELECT c FROM Category c WHERE (c.userId IS NULL OR c.userId = :userId) AND c.active = true "
            + "ORDER BY c.kind, c.name")
    List<Category> findVisible(Long userId);

    // One category the user can see; another user's category is not found (404)
    @Query("SELECT c FROM Category c WHERE c.id = :id AND (c.userId IS NULL OR c.userId = :userId) AND c.active = true")
    Optional<Category> findVisibleById(Long id, Long userId);

    // Name already used by a default or by the user's own category of the same kind.
    // excludeId = the category being edited (pass 0 when creating a new one).
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE (c.userId IS NULL OR c.userId = :userId) "
            + "AND c.kind = :kind AND c.name = :name AND c.id <> :excludeId")
    boolean nameTaken(Long userId, CategoryKind kind, String name, Long excludeId);

    // Plain SQL: transactions (T10), budgets and bills (T17/T18) that point at this category
    @Query(value = "SELECT (SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId)"
            + " + (SELECT COUNT(*) FROM budgets WHERE category_id = :categoryId)"
            + " + (SELECT COUNT(*) FROM bills WHERE category_id = :categoryId)", nativeQuery = true)
    long countUses(Long categoryId);
}
