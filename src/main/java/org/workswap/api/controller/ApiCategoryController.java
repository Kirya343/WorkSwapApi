package org.workswap.api.controller;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.CategoryDTO;
import org.workswap.core.services.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class ApiCategoryController {
    
    private final CategoryService categoryService;

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
}