package org.workswap.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.core.services.ListingService;
import org.workswap.core.services.ReviewService;
import org.workswap.core.services.StatService;
import org.workswap.datasource.central.model.Review;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final StatService statService;
    private final ListingService listingService;

    @PreAuthorize("hasAuthority('CREATE_REVIEW')")
    @PostMapping("/create")
    public ResponseEntity<?> addReview(@RequestParam(required = false) Long listingId,
                                        @RequestParam(required = false) Long profileId,
                                        @RequestParam Long authorId,
                                        @RequestParam String text, // Текст отзыва
                                        @RequestParam double rating, // Рейтинг отзыва
                                        @RequestHeader(value = "referer", required = false) String referer) {

        Review review = reviewService.createReview(authorId, profileId, listingId, rating, text);

        if (review == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Не удалось создать отзыв");
        }

        statService.updateRatingForListing(listingService.findListing(listingId.toString()));

        // Перенаправляем обратно на страницу объявления
        return ResponseEntity.ok().build();
    }
}
