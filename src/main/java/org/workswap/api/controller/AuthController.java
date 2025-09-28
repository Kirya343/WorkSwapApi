package org.workswap.api.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.core.services.command.UserCommandService;
import org.workswap.core.services.security.AuthCookiesService;
import org.workswap.core.services.security.JwtService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.datasource.central.model.User;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthCookiesService cookiesService;
    private final JwtService jwtService; // твой сервис для парсинга и валидации refresh-токена
    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @Value("${api.url}")
    private String apiUrl;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.domain}")
    private String cookieDomain;

    @Value("${app.cookie.sameSite}")
    private String cookieSameSite;

    @GetMapping("/authorize")
    @PermitAll
    public void redirectToGoogle(@RequestParam(required = false, defaultValue = "/") String redirect,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {

        logger.debug("redirect: " + redirect);

        // создаём state, можно зашифровать или просто сохранить в сессии
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(redirect.getBytes(StandardCharsets.UTF_8));

        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

        request.getSession().setAttribute("redirectUrl", encodedState);

        String oldRefreshToken = getTokenFromCookies(request, "refreshToken");
        Long userId = jwtService.validateAndGetUserId(oldRefreshToken);

        request.getSession().setAttribute("tempUserId", userId);

        response.sendRedirect("/oauth2/authorization/google");
    }

    @PostMapping("/refresh")
    @PermitAll
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        logger.debug("Обновляем токен пользователя");
        try {
            String refreshToken = getTokenFromCookies(request, "refreshToken");
            logger.debug("Токен найден? {}", refreshToken);

            String userId = null;
            if (refreshToken != null) {
                userId = jwtService.validateAndGetUserIdStr(refreshToken); // 2. валидируем refresh token и получаем email
            }

            logger.debug("userId: {}", userId);

            User user = null;

            if (userId != null) {
                user = userQueryService.findUser(userId);
                logger.debug("Пользователь найден");
            }

            if (user == null) {
                user = userCommandService.createTempUser();
                logger.debug("Пользователь не найден, создаём временного");
            }

            logger.debug("Айди пользователя: {}", user.getId());

            cookiesService.setAuthCookies(response, user); // 4. обновляем куки с токенами

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Token generation error: " + e.getMessage());
        }
    }

    private String getTokenFromCookies(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @PostMapping("/logout")
    @PermitAll
    public ResponseEntity<?> logout(HttpServletResponse response) {

        try {
            
            cookiesService.deleteAuthCookies(response);

            return ResponseEntity.ok(Map.of("message", "Вы успешно вышли из аккаунта"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Token generation error: " + e.getMessage());
        }
    }
}