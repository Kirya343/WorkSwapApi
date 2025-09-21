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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.user.UserDTO;
import org.workswap.common.enums.UserStatus;
import org.workswap.core.services.command.UserCommandService;
import org.workswap.core.services.mapping.UserMappingService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.core.someClasses.WebhookSigner;
import org.workswap.datasource.central.model.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UsersController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final UserMappingService userMappingService;

    @PostMapping("/telegram/connect")
    @PreAuthorize("hasAuthority('CONNECT_TELEGRAM')")
    public ResponseEntity<?> telegramConnect(@AuthenticationPrincipal User user) {
        String email = user.getEmail();

        String body = "{\"websiteUserId\":\"" + email + "\"}";
        String signature = WebhookSigner.generateSignature(body);

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://89.35.130.223:30003/api/users/generate-token"))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode json = objectMapper.readTree(response.body());

            String linkUrl = json.path("data").path("linkUrl").asText();

            user.getSettings().setTelegramConnected(true);
            userCommandService.save(user);

            return ResponseEntity.ok(Map.of("link", linkUrl)); // Отправляем ссылку клиенту

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при отправке запроса");
        }
    }

    @GetMapping("/telegram/check")
    @PreAuthorize("hasAuthority('CONNECT_TELEGRAM')")
    public ResponseEntity<?> checkTelegramConnect(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("telegramConnected", user.getSettings().isTelegramConnected()));
    }

    @PostMapping("/accept-terms")
    @PreAuthorize("hasAuthority('ACCEPT_TERMS')")
    public ResponseEntity<?> acceptTerms(@AuthenticationPrincipal User user) {
        
        user.setTermsAcceptanceDate(LocalDateTime.now());
        user.setTermsAccepted(true);
        
        userCommandService.save(user);
        
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/current/delete")
    @PreAuthorize("hasAuthority('DELETE_OWN_ACCOUNT')")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal User user) {
        String email = new String(user.getEmail());
        userCommandService.deleteUser(userQueryService.findUser(user.getEmail()));

        return ResponseEntity.ok(Map.of("success", true, "message", "Аккаунт " + email + " успешно удалён!"));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('GET_CURRENT_USER')")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user != null) {
            return ResponseEntity.ok(Map.of("user", userMappingService.toDto(user)));
        }
        return ResponseEntity.internalServerError().body(Map.of("message", "Пользователь не найден"));
    }

    @GetMapping("/current/settings")
    @PreAuthorize("hasAuthority('GET_CURRENT_USER_SETTINGS')")
    public ResponseEntity<?> getCurrentUserSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("user", userMappingService.toFullDto(user)));
    }

    @GetMapping("/get/{id}")
    @PermitAll
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("user", userMappingService.toDto(userQueryService.findUser(id.toString()))));
    }

    @GetMapping("/recent/{amount}")
    @PreAuthorize("hasAuthority('GET_RECENT_USERS')")
    public ResponseEntity<?> getRecentListings(
        @PathVariable int amount
    ) {
        List<UserDTO> users = userQueryService.getRecentUsers(amount)
                                         .stream()
                                         .map(userMappingService::toDto)
                                         .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("users", users));
    }

    @PatchMapping("/modify")
    @PreAuthorize("hasAuthority('UPDATE_USER_SETTINGS')")
    public ResponseEntity<?> modifyUser(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> updates
        ) {

        userCommandService.modifyUserParam(user, updates);
        
        return ResponseEntity.ok(Map.of("message", "Объявление успешно обновлено"));
    }

    //создать кастомную роль временного юзера и сделать ей пермишн разрешающий использовать этот метод
    @PatchMapping("/register")
    @PermitAll //@PreAuthorize("hasAuthority('REGISTER_CURRENT_USER')")
    public ResponseEntity<?> registerUser(
            @AuthenticationPrincipal User user
        ) {

        if (user == null) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Пользователь не найден"));
        }

        User updatedUser = userCommandService.registerUser(user);

        if (updatedUser.getStatus() == UserStatus.ACTIVE) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Вы успешно зарегистрировались!"));
        }
        
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Ошибка регистрации пользователя"));
    }
}
