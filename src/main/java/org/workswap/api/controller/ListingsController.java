package org.workswap.api.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.workswap.common.dto.ListingTranslationDTO;
import org.workswap.core.services.UserService;
import org.workswap.core.services.command.ListingCommandService;
import org.workswap.core.services.mapping.ListingMappingService;
import org.workswap.core.services.query.ListingQueryService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.chat.Chat;
import org.workswap.datasource.central.repository.chat.ChatRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/listing")
public class ListingsController {
    
    private final ChatRepository chatRepository;
    private final UserService userService;

    private final ListingQueryService listingQueryService;
    private final ListingCommandService listingCommandService;
    private final ListingMappingService listingMappingService;

    @PermitAll
    @GetMapping("/get/{id}")
    public ResponseEntity<?> getListing(@PathVariable Long id, @RequestParam String locale, Model model) {

        ListingDTO listing = listingQueryService.getListingDTO(id, locale);

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
            listing = listingMappingService.convertToDTO(conv.getListing(), Locale.of(lang));
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
        List<ListingDTO> listings = listingQueryService.getSortedCatalogDto(
            user, location, lang, page, category, sortBy, searchQuery, hasReviews);

        Map<String, Object> response = new HashMap<>();

        response.put("listings", listings);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    public ResponseEntity<?> getDraftListings(@AuthenticationPrincipal User user, @RequestParam String locale) {

        List<ListingDTO> drafts = listingQueryService.getDrafts(user, locale);

        return ResponseEntity.ok(Map.of("listings", drafts));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createListing(@AuthenticationPrincipal User authUser) {
        Listing listing = new Listing(authUser);
        Listing savedListing = listingCommandService.saveAndReturn(listing);
        return ResponseEntity.ok(Map.of("message", "Черновик объявления сохранён", "id", savedListing.getId()));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingQueryService.findListing(id.toString());
        User user = userService.findUser(authUser.getEmail());

        listingCommandService.toggleFavorite(user, listing);
        return ResponseEntity.ok(Map.of("message", "Избранное обновлено"));
    }

    @PostMapping("/publish/{id}")
    public ResponseEntity<?> publishListing(@PathVariable Long id) {
        Listing listing = listingQueryService.findListing(id.toString());
        listing.setTemporary(false);
        listingCommandService.save(listing);
        return ResponseEntity.ok(Map.of("message", "Объявление опубликовано"));
    }

    @GetMapping("/{id}/favorite/status")
    public ResponseEntity<?> isFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingQueryService.findListing(id.toString());
        User user = userService.findUser(authUser.getEmail());

        boolean isFavorite = listingQueryService.isFavorite(user, listing);
        return  ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> deleteListing(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        try {
            Listing listing = listingQueryService.findListing(id.toString());

            if (!listing.getAuthor().equals(user)) {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Вы не можете удалить это объявление");
            }

            if (listing.getAuthor().equals(user)) {
                listingCommandService.delete(listing);
                return ResponseEntity.ok().body("Объявление успешно удалено!");
            }
            return ResponseEntity.ok(Map.of("message", "Объявление успешно удалено!"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "Ошибка при удалении объявления!"));
        }
    }

    @PreAuthorize("hasAuthority('UPDATE_LISTING')")
    @PostMapping("/update/{id}")
    public ResponseEntity<?> modifyListing(@PathVariable Long id) {
        try {
            listingCommandService.save(listingQueryService.findListing(id.toString()));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.ok().body("Ошибка при обновлении объявления: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LISTING')")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteListing(@PathVariable Long id) {
        listingCommandService.delete(listingQueryService.findListing(id.toString()));
        return ResponseEntity.ok(Map.of("message", "Объявление удалено"));
    }

    //пометить пермишном
    @GetMapping("/recent/{amount}")
    public ResponseEntity<?> getRecentListings(
        @PathVariable int amount, 
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getRecentListings(amount, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/my-listings")
    public ResponseEntity<?> getMyListings(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getListingDtosByUser(user.getId(), locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/by-user/{id}")
    public ResponseEntity<?> getListingsByUser(
        @PathVariable Long id,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getListingDtosByUser(id, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getFavorites(user, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<?> getImages(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        List<ImageDTO> images = listingQueryService.getImages(id);

        return ResponseEntity.ok(Map.of("images", images));
    }

    @GetMapping("/translations/{id}")
    public ResponseEntity<?> getTranslations(@PathVariable Long id) {
        List<Map<String, ListingTranslationDTO>> translations = listingQueryService.getTranslations(id);

        return ResponseEntity.ok(Map.of("translations", translations));
    }

    @PatchMapping("/modify/{id}")
    public ResponseEntity<?> modifyListing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates
    ) {
        listingCommandService.modifyListingParam(id, updates);
        
        return ResponseEntity.ok(Map.of("message", "Объявление успешно обновлено"));
    }
}
