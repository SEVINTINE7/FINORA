package lk.finora.api.category;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.category.dto.CategoryResponse;
import lk.finora.api.category.dto.CreateCategoryRequest;
import lk.finora.api.category.dto.UpdateCategoryRequest;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;

/** Default + own categories (T09.2, T09.3). Defaults are read-only for users. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        List<CategoryResponse> result = new ArrayList<>();
        for (Category category : categoryRepository.findVisible(userId)) {
            result.add(toResponse(category));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long userId, Long id) {
        return toResponse(findVisible(userId, id));
    }

    @Transactional
    public CategoryResponse create(Long userId, CreateCategoryRequest request) {
        String name = request.name().trim();
        CategoryKind kind = CategoryKind.valueOf(request.kind());
        // 0 = no category to skip, because this one is new
        if (categoryRepository.nameTaken(userId, kind, name, 0L)) {
            throw duplicateName();
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        category.setNameSi(request.nameSi());
        category.setKind(kind);
        category.setNeedOrWant(toNeedOrWant(request.needOrWant()));
        categoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, UpdateCategoryRequest request) {
        Category category = findOwn(userId, id);
        String name = request.name().trim();
        if (categoryRepository.nameTaken(userId, category.getKind(), name, id)) {
            throw duplicateName();
        }

        category.setName(name);
        category.setNameSi(request.nameSi());
        category.setNeedOrWant(toNeedOrWant(request.needOrWant()));
        categoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwn(userId, id);
        if (categoryRepository.countUses(id) > 0) {
            throw new ApiException(ErrorCodes.IN_USE, HttpStatus.CONFLICT,
                    "This category is used by transactions, budgets or bills");
        }
        categoryRepository.delete(category);
    }

    private Category findVisible(Long userId, Long id) {
        return categoryRepository.findVisibleById(id, userId)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
    }

    /** For edit/delete: must be the user's own category, not a default. */
    private Category findOwn(Long userId, Long id) {
        Category category = findVisible(userId, id);
        if (category.isDefault()) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN, "Default categories cannot be changed");
        }
        return category;
    }

    private NeedOrWant toNeedOrWant(String value) {
        if (value == null) {
            return null;
        }
        return NeedOrWant.valueOf(value);
    }

    private ApiException duplicateName() {
        return new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT, "A category with this name already exists");
    }

    private CategoryResponse toResponse(Category category) {
        String needOrWant = category.getNeedOrWant() == null ? null : category.getNeedOrWant().name();
        return new CategoryResponse(category.getId(), category.getName(), category.getNameSi(),
                category.getKind().name(), needOrWant, category.isDefault());
    }
}
