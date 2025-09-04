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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.ImageDTO;
import org.workswap.common.dto.ListingDTO;
import org.workswap.core.services.CategoryService;
import org.workswap.core.services.ListingService;
import org.workswap.core.services.LocationService;
import org.workswap.core.services.UserService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.chat.Chat;
import org.workswap.datasource.central.model.listingModels.Category;
import org.workswap.datasource.central.model.listingModels.ListingTranslation;
import org.workswap.datasource.central.model.listingModels.Location;
import org.workswap.datasource.central.repository.ListingTranslationRepository;
import org.workswap.datasource.central.repository.chat.ChatRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/listing")
public class ListingsController {
    
    private final ChatRepository chatRepository;
    private final ListingService listingService;
    private final LocationService locationService;
    private final CategoryService categoryService;
    private final UserService userService;

    // Я понимаю что использовать в репозитории в контроллерах нехорошо, но создавать целый сервис ради одного метода сохранения - ещё хуже
    private final ListingTranslationRepository listingTranslationRepository;

    @PermitAll
    @GetMapping("/get/{listingId}")
    public ResponseEntity<?> getListing(@PathVariable Long listingId, @RequestParam("locale") String lang, Model model) {

        ListingDTO listing = listingService.convertToDTO(listingService.findListing(listingId.toString()), Locale.of(lang));

        if (listing == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().body(Map.of("listing", listing));
    }

    @PermitAll
    @GetMapping("/chat/get/{chatId}")
    public ResponseEntity<?> getListingFromChat(@PathVariable Long chatId, @RequestParam("locale") String lang, Model model) {

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

    @PermitAll
    @GetMapping("/catalog")
    public ResponseEntity<?> sortCatalogAjax(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false, defaultValue = "false") boolean hasReviews,
            @RequestParam(required = false) String location,
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
                locationType = locationService.findLocation(location);
            }

            if (!languages.contains(lang)) {
                languages.add(lang);
            }

            Pageable pageable = PageRequest.of(page, 12);

            Category categoryType = categoryService.findCategory(category);

            Page<Listing> listingsPage = listingService.findPageOfSortedListings(categoryType, sortBy, pageable, locationType, searchQuery, hasReviews, languages);

            List<ListingDTO> listings = new ArrayList<>();
            for(Listing l : listingsPage.getContent()) {
                listings.add(listingService.convertToDTO(l, Locale.of(lang)));
            }

            Map<String, Object> response = new HashMap<>();

            response.put("listings", listings);

            return ResponseEntity.ok(response);
        } catch (Exception e) {

            e.printStackTrace();
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Ошибка загрузки каталога");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    @GetMapping("/drafts")
    public ResponseEntity<?> getDraftListings(@AuthenticationPrincipal User authUser, @RequestParam String locale) {

        List<ListingDTO> drafts = listingService.findDrafts(authUser)
                                                .stream()
                                                .map(listing -> listingService.convertToDTO(listing, Locale.of(locale)))
                                                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("listings", drafts));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createListing(@AuthenticationPrincipal User authUser) {
        Listing listing = new Listing(authUser);
        Listing savedListing = listingService.saveAndReturn(listing);
        return ResponseEntity.ok(Map.of("message", "Черновик объявления сохранён", "id", savedListing.getId()));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingService.findListing(id.toString());
        User user = userService.findUser(authUser.getEmail());

        listingService.toggleFavorite(user, listing);
        return  ResponseEntity.ok(Map.of("message", "Избранное обновлено"));
    }

    @GetMapping("/{id}/favorite/status")
    public ResponseEntity<?> isFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingService.findListing(id.toString());
        User user = userService.findUser(authUser.getEmail());

        boolean isFavorite = listingService.isFavorite(user, listing);
        return  ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> deleteListing(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        try {
            Listing listing = listingService.findListing(id.toString());

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

    @GetMapping("/my-listings")
    public ResponseEntity<?> getMyListings(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingService.findMyListings(user)
                                                  .stream()
                                                  .map(listing -> listingService.convertToDTO(listing, Locale.of(locale)))
                                                  .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/by-user/{id}")
    public ResponseEntity<?> getListingsByUser(
        @PathVariable Long id,
        @RequestParam String locale
    ) {
        User user = userService.findUser(id.toString());
        
        List<ListingDTO> listings = listingService.findListingsByUser(user)
                                                  .stream()
                                                  .map(listing -> listingService.convertToDTO(listing, Locale.of(locale)))
                                                  .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingService.findFavoritesListingsByUser(user)
                                                  .stream()
                                                  .map(listing -> listingService.convertToDTO(listing, Locale.of(locale)))
                                                  .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<?> getImages(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @RequestParam String locale
    ) {
        Listing listing = listingService.findListing(id.toString());

        List<ImageDTO> images = listing.getImages()
                                        .stream()
                                        .map(image -> new ImageDTO(image.getId(), id, image.getPath()))
                                        .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("images", images));
    }

    @PatchMapping("/modify/{id}")
    public ResponseEntity<?> modifyListing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {

        Listing listing = listingService.findListing(id.toString());

        if(listing != null) {
            updates.forEach((key, value) -> {
                switch (key) {
                    case "translation":
                        if (value instanceof Map) {
                            Map<?, ?> rawMap = (Map<?, ?>) value;

                            rawMap.forEach((lang, v) -> {
                                String language = (String) lang;
                                if (v instanceof Map) {
                                    Map<?, ?> vMap = (Map<?, ?>) v;
                                    String title = (String) vMap.get("title");
                                    String description = (String) vMap.get("description");

                                    ListingTranslation translation = new ListingTranslation(language, title, description, listing);
                                    listingTranslationRepository.save(translation);
                                }
                            });
                        }
                        break;
                    case "price":
                        if (value != null) {
                            Double price;
                            if (value instanceof Number) {
                                price = ((Number) value).doubleValue();
                            } else {
                                price = Double.parseDouble(value.toString());
                            }
                            listing.setPrice(price);
                        }
                        break;
                    case "mainImageUrl":
                        listing.setImagePath((String) value);
                        break;
                    case "active":
                        listing.setActive((Boolean) value);
                        break;
                    case "testMode":
                        listing.setTestMode((Boolean) value);
                        break;
                    case "location":
                        if (value != null) {
                            Long locId = ((Number) value).longValue(); // безопасно для Integer и Long
                            Location loc = locationService.findLocation(locId.toString());
                            listing.setLocation(loc);
                        }
                        break;
                    case "category":
                        if (value != null) {
                            Long catId = ((Number) value).longValue(); // безопасно для Integer и Long
                            Category cat = categoryService.findCategory(catId.toString());
                            listing.setCategory(cat);
                        }
                        break;
                }
            });
        }

        listingService.save(listing);
        return ResponseEntity.ok(Map.of("message", "Объявление успешно обновлено"));
    }
}
