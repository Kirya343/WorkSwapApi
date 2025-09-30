package org.workswap.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.ReviewDTO;
import org.workswap.core.services.ReviewService;
import org.workswap.core.services.producers.ReviewProducer;
import org.workswap.datasource.central.model.Review;

import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewProducer reviewProducer;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_REVIEW')")
    public ResponseEntity<?> addReview(
        @RequestParam(required = false) Long listingId,
        @RequestParam(required = false) Long profileId,
        @RequestParam Long authorId,
        @RequestParam String text, // Текст отзыва
        @RequestParam double rating // Рейтинг отзыва
    ) {

        Review review = reviewService.createReview(authorId, profileId, listingId, rating, text);

        if (review == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "message", "reviewCreate", 
                    "status", "success"
                ));
        }

        reviewProducer.reviewCreated(reviewService.convertToDTO(review));

        // Перенаправляем обратно на страницу объявления
        return ResponseEntity.ok(Map.of("message", "reviewCreate", "status", "success"));
    }

    @GetMapping("/list")
    @PermitAll
    public ResponseEntity<?> getRewiewsByItem(
        @RequestParam(required = false) Long listingId,
        @RequestParam(required = false) Long profileId
    ) {

        List<Review> reviews = new ArrayList<>();

        if (listingId != null) {
            reviews = reviewService.getReviewsByListingId(listingId);
        } else if (profileId != null) {
            reviews = reviewService.getReviewsByProfileId(profileId);
        }

        List<ReviewDTO> dtos = reviews.stream()
                                      .map(r -> reviewService.convertToDTO(r))
                                      .toList();

        return ResponseEntity.ok().body(Map.of("reviews", dtos));
    }
}
