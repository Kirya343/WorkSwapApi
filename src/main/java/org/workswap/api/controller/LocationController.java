package org.workswap.api.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.LocationDTO;
import org.workswap.datasource.central.model.listingModels.Location;
import org.workswap.datasource.central.repository.LocationRepository;
import org.workswap.core.services.LocationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locations")
public class LocationController {
    
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

    @PreAuthorize("hasAuthority('CREATE_LOCATION')")
    @PostMapping("/add")
    public ResponseEntity<?> addLocation(@RequestParam(required = false) Long countryId,
                                @RequestParam String name) {
        try {
            Location country = locationRepository.findById(countryId).orElse(null);
            Location location = new Location(name, countryId != null ? true : false, country);
            if (locationRepository.findByName(location.getName()) != null) {
                return ResponseEntity.status(HttpStatus.IM_USED).body(Map.of("errorMessage", "Такая локация уже существует"));
            }
            locationRepository.save(location);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", "Ошибка при добавлении локации"));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LOCATION')")
    @GetMapping("/{id}/delete")
    public ResponseEntity<?> deleteLocation(@PathVariable Long id) {
        try {
            locationRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", "Ошибка при удалении локации"));
        }
    }
}