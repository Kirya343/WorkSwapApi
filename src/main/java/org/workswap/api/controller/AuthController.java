package org.workswap.api.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.core.services.components.security.JwtIssuer;
import org.workswap.core.services.components.security.JwtService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.datasource.central.model.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final JwtIssuer jwtIssuer;
    private final JwtService jwtService; // твой сервис для парсинга и валидации refresh-токена
    private final UserQueryService userQueryService;

    @Value("${api.url}")
    private String apiUrl;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.domain}")
    private String cookieDomain;

    @Value("${app.cookie.sameSite}")
    private String cookieSameSite;

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

        response.sendRedirect("/oauth2/authorization/google");
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

        logger.debug("email пользователя: {}", email);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        // 3. находим пользователя
        User user = userQueryService.findUser(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        try {
            // 4. создаем новый access-токен
            String newAccessToken = jwtIssuer.issueAccessToken(user);

            // (опционально) обновляем refresh-токен и кладем в cookie заново
            String newRefreshToken = jwtIssuer.issueRefreshToken(user);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .sameSite(cookieSameSite)
                .maxAge(Duration.ofDays(30))
                .build();

            response.addHeader("Set-Cookie", cookie.toString());

            // 5. возвращаем access-токен в JSON
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Token generation error: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(0) // удалить cookie
            .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("message", "Вы успешно вышли из аккаунта"));
    }
}