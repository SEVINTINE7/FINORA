package lk.finora.api.category;

import java.sql.Statement;
import java.util.List;
import lk.finora.api.category.AdminCategoryController.DefaultCategory;
import lk.finora.api.common.error.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminCategoryService {
    private final JdbcTemplate jdbc;
    private final CategoryRepository categories;

    AdminCategoryService(JdbcTemplate jdbc, CategoryRepository categories) {
        this.jdbc = jdbc;
        this.categories = categories;
    }

    List<DefaultCategory> list() {
        return jdbc.query("""
                SELECT id, name, name_si, kind, active FROM categories WHERE user_id IS NULL ORDER BY kind, name
                """, (row, index) -> new DefaultCategory(row.getLong("id"), row.getString("name"),
                row.getString("name_si"), CategoryKind.valueOf(row.getString("kind")), row.getBoolean("active")));
    }

    @Transactional
    public DefaultCategory create(CategoryKind kind, String name, String nameSi) {
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var insert = connection.prepareStatement(
                        "INSERT INTO categories (name, name_si, kind) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                insert.setString(1, name.strip());
                insert.setString(2, nameSi.strip());
                insert.setString(3, kind.name());
                return insert;
            }, key);
        } catch (DuplicateKeyException duplicate) {
            throw conflict();
        }
        return find(java.util.Objects.requireNonNull(key.getKey()).longValue());
    }

    @Transactional
    public DefaultCategory rename(long id, String name, String nameSi) {
        find(id);
        try {
            jdbc.update("UPDATE categories SET name = ?, name_si = ? WHERE id = ? AND user_id IS NULL",
                    name.strip(), nameSi.strip(), id);
        } catch (DuplicateKeyException duplicate) {
            throw conflict();
        }
        return find(id);
    }

    @Transactional
    public void remove(long id) {
        find(id);
        if (categories.countUses(id) == 0) {
            jdbc.update("DELETE FROM categories WHERE id = ? AND user_id IS NULL", id);
        } else {
            jdbc.update("UPDATE categories SET active = FALSE WHERE id = ? AND user_id IS NULL", id);
        }
    }

    private DefaultCategory find(long id) {
        return jdbc.query("""
                SELECT id, name, name_si, kind, active FROM categories WHERE id = ? AND user_id IS NULL FOR UPDATE
                """, (row, index) -> new DefaultCategory(row.getLong("id"), row.getString("name"),
                row.getString("name_si"), CategoryKind.valueOf(row.getString("kind")), row.getBoolean("active")), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Default category not found"));
    }

    private static ApiException conflict() {
        return new ApiException("CONFLICT", HttpStatus.CONFLICT, "A default category already has this name");
    }
}
