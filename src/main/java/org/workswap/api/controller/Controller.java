package org.workswap.api.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.datasource.central.model.User;
import org.workswap.core.services.UserService;
import org.workswap.core.someClasses.WebhookSigner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class Controller {

    private final UserService userService;

    @PostMapping("/api/users/accept-terms")
    public ResponseEntity<?> acceptTerms(Principal principal) {
        String sub = principal.getName();

        User user = userService.findUser(sub);
        
        user.setTermsAcceptanceDate(LocalDateTime.now());
        user.setTermsAccepted(true);
        
        userService.save(user);
        
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('CONNECT_TELEGRAM')")
    @PostMapping("/api/telegram/connect")
    public ResponseEntity<?> telegramConnect(@AuthenticationPrincipal OAuth2User oAuth2User) {
        User user = userService.findUserFromOAuth2(oAuth2User);
        String email = user.getEmail();

        String body = "{\"websiteUserId\":\"" + email + "\"}";
        String signature = WebhookSigner.generateSignature(body);

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://s1.qwer-host.xyz:25079/api/users/generate-token"))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode json = objectMapper.readTree(response.body());

            String linkUrl = json.path("data").path("linkUrl").asText();

            user.setTelegramConnected(true);
            userService.save(user);

            return ResponseEntity.ok(linkUrl); // Отправляем ссылку клиенту

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при отправке запроса");
        }
    }
}
