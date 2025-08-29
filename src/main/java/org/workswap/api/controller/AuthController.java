package org.workswap.api.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.workswap.core.services.UserService;
import org.workswap.core.services.components.security.JwtIssuer;
import org.workswap.core.services.components.security.JwtService;
import org.workswap.datasource.central.model.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtIssuer jwtIssuer;
    private final JwtService jwtService; // твой сервис для парсинга и валидации refresh-токена
    private final UserService userService;

    @Value("${api.url}")
    private String apiUrl;

    private final ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/authorize")
    public void redirectToGoogle(@RequestParam(required = false, defaultValue = "/") String redirect,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {

        System.out.println("redirect: " + redirect);

        // создаём state, можно зашифровать или просто сохранить в сессии
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(redirect.getBytes(StandardCharsets.UTF_8));
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

        request.getSession().setAttribute("redirectUrl", encodedState);

        String authorizationUri = UriComponentsBuilder
                .fromUriString("/oauth2/authorization/google")
                .queryParam("response_type", "code")
                .queryParam("client_id", ((ClientRegistration) ((InMemoryClientRegistrationRepository) clientRegistrationRepository)
                        .findByRegistrationId("google")).getClientId())
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", apiUrl + "/login/oauth2/code/google")
                .build()
                .toUriString();

        response.sendRedirect(authorizationUri);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // 1. достаем cookie
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No cookies found");
        }

        String refreshToken = Arrays.stream(cookies)
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No refresh token");
        }

        // 2. валидируем refresh токен
        String email = jwtService.validateAndGetEmail(refreshToken);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        // 3. находим пользователя
        User user = userService.findUser(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        try {
            // 4. создаем новый access-токен
            String newAccessToken = jwtIssuer.issueAccessToken(user);

            // (опционально) обновляем refresh-токен и кладем в cookie заново
            String newRefreshToken = jwtIssuer.issueRefreshToken(user);
            Cookie newCookie = new Cookie("refreshToken", newRefreshToken);
            newCookie.setHttpOnly(true);
            // только для разработки для доступа без https
            newCookie.setSecure(false);
            newCookie.setPath("/api/auth/refresh");
            newCookie.setMaxAge((int) Duration.ofDays(30).getSeconds());
            response.addCookie(newCookie);

            // 5. возвращаем access-токен в JSON
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Token generation error: " + e.getMessage());
        }
    }
}