package org.workswap.api.controller;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.enums.PriceType;

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
}
