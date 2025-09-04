package org.workswap.api.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.enums.PriceType;
import org.workswap.config.LocalisationConfig.LanguageUtils;
import org.workswap.datasource.central.model.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class MetadataSettingsController {

    @GetMapping("/price-types")
    public ResponseEntity<?> getPriceTypes() {

        var types = Arrays.stream(PriceType.values())
            .map(pt -> Map.of(
                "name", pt.name(),
                "displayName", pt.getDisplayName()
            ))
            .toList();

        return ResponseEntity.ok(Map.of("priceTypes", types));
    }

    @GetMapping("/languages")
    public ResponseEntity<?> getLanguages(@AuthenticationPrincipal User user) {

        List<String> langs = user.getLanguages();
        if (langs == null || langs.isEmpty()) {
            langs = LanguageUtils.SUPPORTED_LANGUAGES;
        }

        return ResponseEntity.ok(Map.of("langs", langs));
    }
}
