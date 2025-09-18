package org.workswap.api.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.listing.CatalogListingDTO;
import org.workswap.common.dto.listing.ImageDTO;
import org.workswap.common.dto.listing.ListingDTO;
import org.workswap.common.dto.listing.ListingTranslationDTO;
import org.workswap.core.services.command.ListingCommandService;
import org.workswap.core.services.mapping.ListingMappingService;
import org.workswap.core.services.query.ListingQueryService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.chat.Chat;
import org.workswap.datasource.central.repository.chat.ChatRepository;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/listing")
public class ListingsController {
    
    private final ChatRepository chatRepository;
    private final UserQueryService userQueryService;

    private final ListingQueryService listingQueryService;
    private final ListingCommandService listingCommandService;
    private final ListingMappingService listingMappingService;

    @GetMapping("/get/{id}")
    @PermitAll
    public ResponseEntity<?> getListing(@PathVariable Long id, @RequestParam String locale) {

        ListingDTO listing = listingQueryService.getListingDTO(id, locale);

        if (listing == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().body(Map.of("listing", listing));
    }

    @GetMapping("/chat/get/{chatId}")
    @PreAuthorize("hasAuthority('GET_LISTING_BY_CHAT')")
    public ResponseEntity<?> getListingFromChat(@PathVariable Long chatId, @RequestParam("locale") String lang) {

        Chat conv = chatRepository.findById(chatId).orElse(null);

        ListingDTO listing = null;
        if (conv != null) {
            listing = listingMappingService.convertToDTO(conv.getListing(), Locale.of(lang));
        }

        if (listing == null) {
            return ResponseEntity.ok(Map.of("message", "Объявление не найдено"));
        }

        return ResponseEntity.ok(Map.of("listing", listing));
    }

    @GetMapping("/catalog")
    @PermitAll
    public ResponseEntity<?> sortCatalogAjax(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false, defaultValue = "false") boolean hasReviews,
            @RequestParam(required = false) String location,
            @RequestParam("locale") String lang,
            @AuthenticationPrincipal User user
    ) {
        List<CatalogListingDTO> listings = listingQueryService.getSortedCatalogDto(
            user, location, lang, page, categoryId, sortBy, searchQuery, hasReviews);

        Map<String, Object> response = new HashMap<>();

        response.put("listings", listings);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('VIEW_LISTINGS_DRAFTS')")
    public ResponseEntity<?> getDraftListings(@AuthenticationPrincipal User user, @RequestParam String locale) {

        List<ListingDTO> drafts = listingQueryService.getDrafts(user, locale);

        return ResponseEntity.ok(Map.of("listings", drafts));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_LISTING')")
    public ResponseEntity<?> createListing(@AuthenticationPrincipal User authUser) {
        Listing listing = new Listing(authUser);
        Listing savedListing = listingCommandService.saveAndReturn(listing);
        return ResponseEntity.ok(Map.of("message", "Черновик объявления сохранён", "id", savedListing.getId()));
    }

    @PostMapping("/favorite/{id}")
    @PreAuthorize("hasAuthority('FAVORITE_LISTING')")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingQueryService.findListing(id.toString());
        User user = userQueryService.findUser(authUser.getEmail());

        listingCommandService.toggleFavorite(user, listing);
        return ResponseEntity.ok(Map.of("message", "Избранное обновлено"));
    }

    @PostMapping("/publish/{id}")
    @PreAuthorize("hasAuthority('PUBLISH_LISTING')")
    public ResponseEntity<?> publishListing(@PathVariable Long id) {
        Listing listing = listingQueryService.findListing(id.toString());
        listing.setTemporary(false);
        listingCommandService.save(listing);
        return ResponseEntity.ok(Map.of("message", "Объявление опубликовано"));
    }

    @GetMapping("/{id}/favorite/status")
    @PreAuthorize("hasAuthority('CHECK_FAVORITE_LISTING')")
    public ResponseEntity<?> isFavorite(@PathVariable Long id, @AuthenticationPrincipal User authUser) {
        Listing listing = listingQueryService.findListing(id.toString());
        User user = userQueryService.findUser(authUser.getEmail());

        boolean isFavorite = listingQueryService.isFavorite(user, listing);
        return  ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }

    @DeleteMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_LISTING')")
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

    @GetMapping("/recent/{amount}")
    @PreAuthorize("hasAuthority('GET_RECENT_LISTINGS')")
    public ResponseEntity<?> getRecentListings(
        @PathVariable int amount, 
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getRecentListings(amount, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/my-listings")
    @PreAuthorize("hasAuthority('GET_OWN_LISTINGS')")
    public ResponseEntity<?> getMyListings(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getListingDtosByUser(user.getId(), locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/by-user/{id}")
    @PermitAll
    public ResponseEntity<?> getListingsByUser(
        @PathVariable Long id,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getListingDtosByUser(id, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/favorites")
    @PreAuthorize("hasAuthority('GET_FAVORITES_LISTINGS')")
    public ResponseEntity<?> getFavorites(
        @AuthenticationPrincipal User user,
        @RequestParam String locale
    ) {
        List<ListingDTO> listings = listingQueryService.getFavorites(user, locale);

        return ResponseEntity.ok(Map.of("listings", listings));
    }

    @GetMapping("/images/{id}")
    @PermitAll
    public ResponseEntity<?> getImages(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        List<ImageDTO> images = listingQueryService.getImages(id);

        return ResponseEntity.ok(Map.of("images", images));
    }

    @GetMapping("/translations/{id}")
    @PermitAll
    public ResponseEntity<?> getTranslations(@PathVariable Long id) {
        Map<String, ListingTranslationDTO> translations = listingQueryService.getTranslations(id);

        return ResponseEntity.ok(Map.of("translations", translations));
    }

    @PatchMapping("/modify/{id}")
    @PreAuthorize("hasAuthority('UPDATE_LISTING')")
    public ResponseEntity<?> modifyListing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates
    ) {
        listingCommandService.modifyListingParam(id, updates);
        
        return ResponseEntity.ok(Map.of("message", "Объявление успешно обновлено"));
    }
}
