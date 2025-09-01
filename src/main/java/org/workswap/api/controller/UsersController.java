package org.workswap.api.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.UserDTO;
import org.workswap.core.services.UserService;
import org.workswap.core.someClasses.WebhookSigner;
import org.workswap.datasource.central.model.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UsersController {

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

    @PreAuthorize("hasAuthority('UPDATE_USER')")
    @PostMapping("/update/{id}")
    public ResponseEntity<?> modifyUser(@PathVariable Long id,
                           @ModelAttribute User user) {
        try {
            userService.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при обновлении: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_USER')")
    @GetMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(userService.findUser(id.toString()));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при удалении: " + e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("user", userService.convertToDto(user)));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("user", userService.convertToDto(userService.findUser(id.toString()))));
    }

    //пометить пермишном
    @GetMapping("/recent/{amount}")
    public ResponseEntity<?> getRecentListings(
        @PathVariable int amount
    ) {
        List<UserDTO> users = userService.getRecentUsers(amount)
                                         .stream()
                                         .map(userService::convertToDto)
                                         .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("users", users));
    }
}
