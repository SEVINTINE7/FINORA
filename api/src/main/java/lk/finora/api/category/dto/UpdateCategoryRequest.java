package lk.finora.api.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for editing an own category. The kind (INCOME/EXPENSE) cannot change, otherwise old
 * expense records could end up in an income category.
 */
public record UpdateCategoryRequest(

        @NotBlank @Size(max = 60)
        String name,

        @Size(max = 60)
        String nameSi,

        @Pattern(regexp = "NEED|WANT", message = "must be NEED or WANT")
        String needOrWant) {
}
