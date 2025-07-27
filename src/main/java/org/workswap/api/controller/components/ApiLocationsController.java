package org.workswap.api.controller.components;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.datasource.central.model.DTOs.LocationDTO;
import org.workswap.datasource.central.repository.LocationRepository;
import org.workswap.core.services.LocationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locations")
public class ApiLocationsController {
    
    private final LocationService locationService;
    private final LocationRepository locationRepository;

    @GetMapping("/cities/{coutryId}")
    public List<LocationDTO> getChildCategories(@PathVariable Long coutryId, Locale locale) {
        return locationService.getCities(coutryId).stream()
                .map(category -> locationService.toDTO(category))
                .collect(Collectors.toList());
    }

    @GetMapping("/getlocation/{locationId}")
    public LocationDTO getCategoryPath(@PathVariable Long locationId, Locale locale) {
        return locationService.toDTO(locationRepository.findById(locationId).orElse(null));
    }
}
