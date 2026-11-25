package lk.finora.api.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An income or expense category. userId == null means a default category (seeded in V6, managed by
 * Admin in T44); users can read defaults but only change their own. created_at is filled by MySQL.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    // Sinhala name; may be empty for user-made categories
    @Column(name = "name_si")
    private String nameSi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "need_or_want")
    private NeedOrWant needOrWant;

    // false = hidden (Admin can switch off a default in T44)
    @Column(nullable = false)
    private boolean active = true;

    // D55: database-assigned immutable financial identity; never written by JPA.
    @Column(name = "semantic_code", length = 16, insertable = false, updatable = false)
    private String semanticCode;

    public String getSemanticCode() {
        return semanticCode;
    }

    public boolean isDefault() {
        return userId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameSi() {
        return nameSi;
    }

    public void setNameSi(String nameSi) {
        this.nameSi = nameSi;
    }

    public CategoryKind getKind() {
        return kind;
    }

    public void setKind(CategoryKind kind) {
        this.kind = kind;
    }

    public NeedOrWant getNeedOrWant() {
        return needOrWant;
    }

    public void setNeedOrWant(NeedOrWant needOrWant) {
        this.needOrWant = needOrWant;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
