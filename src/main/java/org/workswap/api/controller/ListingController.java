package org.workswap.api.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.ListingDTO;
import org.workswap.core.services.ListingService;
import org.workswap.core.services.UserService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.chat.Chat;
import org.workswap.datasource.central.model.listingModels.Category;
import org.workswap.datasource.central.model.listingModels.Location;
import org.workswap.datasource.central.repository.CategoryRepository;
import org.workswap.datasource.central.repository.LocationRepository;
import org.workswap.datasource.central.repository.chat.ChatRepository;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/listing")
public class ListingController {
    
    private final ChatRepository chatRepository;
    private final ListingService listingService;
    private final LocationRepository locationRepository;
    private final UserService userService;
    private final CategoryRepository categoryRepository;

    @PermitAll
    @GetMapping("/{chatId}/get")
    public ResponseEntity<?> getListing(@PathVariable Long chatId, @RequestParam("locale") String lang, Model model) {

        Chat conv = chatRepository.findById(chatId).orElse(null);

        ListingDTO listing = null;
        if (conv != null) {
            listing = listingService.convertToDTO(conv.getListing(), Locale.of(lang));
        }

        if (listing == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().body(listing);
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> sortCatalogAjax(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false, defaultValue = "false") boolean hasReviews,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Long listingId,
            @RequestParam("locale") String lang,
            @AuthenticationPrincipal User user,
            Model model,
            HttpServletRequest request
    ) {
        try {
            List<String> languages = new ArrayList<>();
            Location locationType = null;
            if (user != null) {
                languages = user.getLanguages();
            }

            if (location == null && user != null) {
                locationType = user.getLocation();
            } else {
                locationType = locationRepository.findByName(location);
            }

            if (!languages.contains(lang)) {
                languages.add(lang);
            }

            Pageable pageable = PageRequest.of(page, 12);

            Category categoryType = categoryRepository.findByName(category);

            Page<Listing> listingsPage = listingService.findPageOfSortedListings(categoryType, sortBy, pageable, locationType, searchQuery, hasReviews, languages);

            List<ListingDTO> listings = new ArrayList<>();
            for(Listing l : listingsPage.getContent()) {
                listings.add(listingService.convertToDTO(l, Locale.of(lang)));
            }

            Map<String, Object> response = new HashMap<>();

            response.put("listings", listings);
            response.put("mainListingId", listingId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Ошибка в API каталога: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Ошибка загрузки каталога");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(userSub);
        Listing listing = listingService.findListing(id.toString());

        listingService.toggleFavorite(user, listing);
        return  ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/favorite/status")
    public ResponseEntity<?> isFavorite(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(userSub);
        Listing listing = listingService.findListing(id.toString());

        boolean isFavorite = listingService.isFavorite(user, listing);
        return  ResponseEntity.ok(isFavorite);
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> deleteListing(
            @PathVariable Long id,
            @RequestHeader("X-User-Sub") String userSub
    ) {
        try {
            Listing listing = listingService.findListing(id.toString());

            // Проверка авторства
            User user = userService.findUser(userSub);

            if (!listing.getAuthor().equals(user)) {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Вы не можете удалить это объявление");
            }

            if (listing.getAuthor().equals(user)) {
                listingService.deleteListing(listing);
                return ResponseEntity.ok().body("Объявление успешно удалено!");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.ok().body("Ошибка при удалении объявления: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('UPDATE_LISTING')")
    @PostMapping("/update/{id}")
    public ResponseEntity<?> modifyListing(@PathVariable Long id) {
        try {
            listingService.save(listingService.findListing(id.toString()));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.ok().body("Ошибка при обновлении объявления: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LISTING')")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteNews(@PathVariable Long id) {
        try {
            listingService.deleteListing(listingService.findListing(id.toString()));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.ok().body("Ошибка при  объявления: " + e.getMessage());
        }
    }

    //пометить пермишном
    @GetMapping("/recent/{amount}")
    public ResponseEntity<?> getRecentListings(
        @PathVariable int amount, 
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingService.getRecentListings(amount)
                                                  .stream()
                                                  .map(listing -> listingService.convertToDTO(listing, Locale.of(locale)))
                                                  .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("listings", listings));
    }
}
