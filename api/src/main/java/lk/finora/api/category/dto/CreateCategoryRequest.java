package lk.finora.api.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for a new own category. nameSi and needOrWant are optional. */
public record CreateCategoryRequest(

        @NotBlank @Size(max = 60)
        String name,

        @Size(max = 60)
        String nameSi,

        @NotNull @Pattern(regexp = "INCOME|EXPENSE", message = "must be INCOME or EXPENSE")
        String kind,

        @Pattern(regexp = "NEED|WANT", message = "must be NEED or WANT")
        String needOrWant) {
}
