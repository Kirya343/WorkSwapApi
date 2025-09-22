package org.workswap.api.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.workswap.core.services.components.MultipartInputStreamFileResource;
import org.workswap.core.services.query.ListingQueryService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.listingModels.Image;
import org.workswap.datasource.central.repository.listing.ImageRepository;

import lombok.RequiredArgsConstructor;;

@RestController
@RequestMapping("/api/cloud")
@RequiredArgsConstructor
public class UploadController {

    private final ImageRepository imageRepository;
    private final ListingQueryService listingQueryService;

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    @Value("${cloud.url}")
    private String cloudUrl;

    //пометить пермишном
    @PostMapping("/upload/listing-image")
    @PreAuthorize("hasAuthority('UPLOAD_LISTING_IMAGE')")
    public ResponseEntity<?> uploadListingImage(
            @RequestParam("image") MultipartFile file,
            @RequestParam(required = false) Long listingId,
            @AuthenticationPrincipal User user
    ) {
        try {
            // 1. Отправляем в клауд
            String imageUrl = uploadToCloud(file, listingId, user.getSub());

            logger.debug(imageUrl);

            // 2. Сохраняем в БД
            Image savedImage = saveImageInDb(imageUrl, listingId);

            logger.debug(savedImage.getId().toString());
            logger.debug(savedImage.getPath());

            return ResponseEntity.ok(Map.of("message", "Изображение успешно сохранено", 
                                            "imageId", savedImage.getId(), 
                                            "imageUrl", savedImage.getPath())); // редирект на страницу объявления
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ошибка при загрузке изображения"));
        }
    }

    private String uploadToCloud(MultipartFile file, Long listingId, String userSub) throws IOException {
        String url = cloudUrl + "/cloud/save/listing-image";

        // multipart тело
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        if (listingId != null) {
            body.add("listingId", listingId.toString());
        }

        // заголовки
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Sub", userSub);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(url, HttpMethod.POST, requestEntity,
                        new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> responseBody = response.getBody();

        if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
            return (String) responseBody.get("url");
        } else {
            throw new RuntimeException("Failed to upload image to cloud: " + response.getStatusCode());
        }
    }

    // Сохраняем Image в базе и возвращаем объект
    private Image saveImageInDb(String imageUrl, Long listingId) {

        Listing listing = listingQueryService.findListing(listingId.toString());

        Image image = new Image(
            imageUrl, 
            listingId != null ? listing : null
        );

        Image savedImage = imageRepository.save(image);

        if (listing.getImagePath() == null) {
            listing.setImagePath(imageUrl);
        }

        return savedImage;
    }

    @DeleteMapping("/delete/listing-image")
    @PreAuthorize("hasAuthority('DELETE_LISTING_IMAGE')")
    public ResponseEntity<?> deleteListingImage(@RequestParam Long imageId, @RequestParam String imageUrl, @AuthenticationPrincipal User user) {
        
        String message = deleteImageFromCloud(imageUrl, user.getSub());

        if (message != null) {
            imageRepository.deleteById(imageId);
        }

        return ResponseEntity.ok(Map.of("message", message));
    }

    private String deleteImageFromCloud(String imageUrl, String userSub) {
        String url = cloudUrl + "/cloud/delete/listing-image?imageUrl=" + imageUrl;

        // заголовки
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Sub", userSub);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(url, HttpMethod.DELETE, requestEntity,
                        new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> responseBody = response.getBody();

        if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
            return (String) responseBody.get("message");
        } else {
            throw new RuntimeException("Failed to upload image to cloud: " + response.getStatusCode());
        }
    }
}