package org.workswap.api.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.workswap.core.services.UserService;
import org.workswap.core.someClasses.WebhookSigner;
import org.workswap.datasource.central.model.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class ApiUserController {

    private final UserService userService;

    @PreAuthorize("hasAuthority('CONNECT_TELEGRAM')")
    @PostMapping("/telegram/connect")
    public ResponseEntity<?> telegramConnect(@RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(userSub);
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

    @PostMapping("/accept-terms")
    public ResponseEntity<?> acceptTerms(@RequestHeader("X-User-Sub") String userSub) {

        User user = userService.findUser(userSub);
        
        user.setTermsAcceptanceDate(LocalDateTime.now());
        user.setTermsAccepted(true);
        
        userService.save(user);
        
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('DELETE_OWN_ACCOUNT')")
    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(@RequestHeader("X-User-Sub") String userSub) throws ServletException {
        userService.deleteUser(userService.findUser(userSub));

        return ResponseEntity.ok().build();
    }
}
