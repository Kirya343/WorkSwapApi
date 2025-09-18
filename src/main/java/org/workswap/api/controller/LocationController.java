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

import jakarta.annotation.security.PermitAll;

import org.workswap.core.services.LocationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locations")
public class LocationController {
    
    private final LocationService locationService;
    private final LocationRepository locationRepository;

    @GetMapping
    public ResponseEntity<?> getAllLocations(Locale locale) {
        
        List<LocationDTO> locs = locationRepository.findAll()
                                                   .stream()
                                                   .map(loc -> locationService.toDTO(loc))
                                                   .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("locations", locs));
    }

    @GetMapping("/countries")
    @PermitAll
    public ResponseEntity<?> getCountires(Locale locale) {

        List<LocationDTO> locs = locationService.getCountries()
                              .stream()
                              .map(loc -> locationService.toDTO(loc))
                              .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("locations", locs));
    }

    @GetMapping("/cities/{coutryId}")
    @PermitAll
    public ResponseEntity<?> getChildCategories(@PathVariable Long coutryId, Locale locale) {

        List<LocationDTO> locs = locationService.getCities(coutryId)
                              .stream()
                              .map(loc -> locationService.toDTO(loc))
                              .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("locations", locs));
    }

    @GetMapping("/{locationId}/get")
    @PermitAll
    public ResponseEntity<?> getCategoryPath(@PathVariable Long locationId, Locale locale) {

        LocationDTO loc = locationService.toDTO(locationRepository.findById(locationId).orElse(null));

        return ResponseEntity.ok(Map.of("location", loc));
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('CREATE_LOCATION')")
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

    @GetMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_LOCATION')")
    public ResponseEntity<?> deleteLocation(@PathVariable Long id) {
        try {
            locationRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("errorMessage", "Ошибка при удалении локации"));
        }
    }
}