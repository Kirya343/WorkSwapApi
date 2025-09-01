package org.workswap.api.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.CategoryDTO;
import org.workswap.core.services.CategoryService;
import org.workswap.datasource.central.model.listingModels.Category;
import org.workswap.datasource.central.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {
    
    private final CategoryService categoryService;

    //перенести сервис в сервис
    private final CategoryRepository categoryRepository;

    @GetMapping("/children/{parentId}")
    public List<CategoryDTO> getChildCategories(@PathVariable Long parentId, Locale locale) {
        return categoryService.getChildCategories(parentId).stream()
                .map(category -> categoryService.toDTO(category, locale))
                .collect(Collectors.toList());
    }

    @GetMapping("/is-leaf/{categoryId}")
    public boolean isLeafCategory(@PathVariable Long categoryId) {
        return categoryService.isLeafCategory(categoryId);
    }

    @GetMapping("/path/{categoryId}")
    public List<CategoryDTO> getCategoryPath(@PathVariable Long categoryId, Locale locale) {
        return categoryService.getCategoryPath(categoryId).stream()
                .map(category -> categoryService.toDTO(category, locale))
                .collect(Collectors.toList());
    }

    //пометить пермишном
    @PostMapping
    public Category createCategory(@RequestBody CategoryDTO dto) {
        Category parent = categoryService.findCategory(dto.getParentId().toString());
        
        Category category = new Category(dto.getName(), parent);
        category.setLeaf(dto.isLeaf());
        return categoryRepository.save(category);
    }

    @PreAuthorize("hasAuthority('CREATE_CATEGORY')")
    @PostMapping("/add")
    public ResponseEntity<?> addCategory(@RequestParam("translations") String translationsRaw,
                              @RequestParam(value = "categoryName") String categoryName,
                              @RequestParam(value = "leaf", defaultValue = "false") Boolean leaf,
                              @RequestParam(value = "parentCategoryId", required = false) Long parentCategoryId) {
        try {
            List<String> translations = Arrays.asList(translationsRaw.split(","));
            CategoryDTO categoryDto = new CategoryDTO();
            categoryDto.setName(categoryName);
            categoryDto.setLeaf(leaf);
            categoryDto.setParentId(parentCategoryId);
            System.out.println("Начинаем создавать категорию");
            categoryService.createCategory(categoryDto, translations);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", "Ошибка при добавлении локации"));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_CATEGORY')")
    @PostMapping("/delete")
    public ResponseEntity<?> deleteCategory(@RequestParam Long category) {
        try {
            categoryService.deleteCategory(category);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", "Ошибка при удалении локации"));
        }
    }

    @PreAuthorize("hasAuthority('VIEW_CATEGORIES')")
    @GetMapping
    public ResponseEntity<?> categoryList() {

        Locale locale = Locale.of("ru");

        List<CategoryDTO> rootCategories = categoryService.getRootCategories()
                                                          .stream()
                                                          .map(category -> categoryService.toDTO(category, locale))
                                                          .collect(Collectors.toList());

        List<CategoryDTO> categories = categoryRepository.findAll()
                                                          .stream()
                                                          .map(category -> categoryService.toDTO(category, locale))
                                                          .collect(Collectors.toList());

        return ResponseEntity.ok().body(Map.of("rootCategories", rootCategories, 
                                               "categories", categories));
    }

    @GetMapping("/root")
    public ResponseEntity<?> rootCategoryList(@RequestParam String locale) {

        List<CategoryDTO> rootCategories = categoryService.getRootCategories()
                                                          .stream()
                                                          .map(category -> categoryService.toDTO(category, Locale.of("locale")))
                                                          .collect(Collectors.toList());

        return ResponseEntity.ok().body(Map.of("rootCategories", rootCategories));
    }
}