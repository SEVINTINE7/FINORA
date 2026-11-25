package lk.finora.api.category;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lk.finora.api.common.error.ApiException;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/categories")
final class AdminCategoryController {
    private final AdminCategoryService categories;

    AdminCategoryController(AdminCategoryService categories) { this.categories = categories; }

    @GetMapping List<DefaultCategory> list() { return categories.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DefaultCategory create(@Valid @RequestBody Create request) {
        return categories.create(request.kind(), request.name(), request.nameSi());
    }

    @PutMapping("/{id}")
    DefaultCategory update(@PathVariable long id, @Valid @RequestBody Names request) {
        return categories.rename(id, request.name(), request.nameSi());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long id) { categories.remove(id); }

    record Names(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 60) String nameSi) {
        @JsonAnySetter void rejectUnknown(String field, JsonNode value) {
            throw ApiException.badRequest("Unexpected category field");
        }
    }
    record Create(@NotNull CategoryKind kind, @NotBlank @Size(max = 60) String name,
                  @NotBlank @Size(max = 60) String nameSi) {
        @JsonAnySetter void rejectUnknown(String field, JsonNode value) {
            throw ApiException.badRequest("Unexpected category field");
        }
    }
    record DefaultCategory(long id, String name, String nameSi, CategoryKind kind, boolean active,
                           String semanticCode) {}
}
