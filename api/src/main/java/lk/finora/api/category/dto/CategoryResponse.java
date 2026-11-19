package lk.finora.api.category.dto;

/** isDefault = true for the built-in categories, which the user cannot edit or delete. */
public record CategoryResponse(Long id, String name, String nameSi, String kind, String needOrWant,
        boolean isDefault) {
}
