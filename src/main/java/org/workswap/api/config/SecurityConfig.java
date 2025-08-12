package org.workswap.api.config;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.user.Permission;
import org.workswap.datasource.central.model.user.Role;
import org.workswap.core.exceptions.UserNotRegisteredException;
import org.workswap.core.services.UserService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final UserService userService;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/secure/**").authenticated()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/accept-terms").authenticated()
                        .requestMatchers(HttpMethod.POST, "/register").permitAll()
                        .anyRequest().permitAll();
                })
                .oauth2Login(oauth2 -> {
                    oauth2.loginPage("/login")
                            .userInfoEndpoint(userInfo -> userInfo
                                    .oidcUserService(oidcUserCreate())
                            )
                            .successHandler((request, response, authentication) -> {
                                SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);

                                if (savedRequest != null) {
                                    // Возвращаем на сохранённую защищённую страницу
                                    new SavedRequestAwareAuthenticationSuccessHandler().onAuthenticationSuccess(request, response, authentication);
                                } else {
                                    // Пробуем вручную сохранённый редирект
                                    String redirectUrl = (String) request.getSession().getAttribute("redirectAfterLogin");
                                    if (redirectUrl != null) {
                                        request.getSession().removeAttribute("redirectAfterLogin");
                                        response.sendRedirect(redirectUrl);
                                    } else {
                                        // Если вообще ничего не найдено — по умолчанию
                                        response.sendRedirect("/catalog");
                                    }
                                }
                            })
                            .failureHandler((request, response, exception) -> {
                                if (exception instanceof UserNotRegisteredException || 
                                    (exception.getCause() != null && exception.getCause() instanceof UserNotRegisteredException)) {
                                    
                                    // Получаем email из исключения
                                    String email = exception instanceof UserNotRegisteredException 
                                        ? ((UserNotRegisteredException) exception).getEmail()
                                        : ((UserNotRegisteredException) exception.getCause()).getEmail();
                                    
                                    // Перенаправляем с сохранением сессии
                                    response.sendRedirect("/register?error=user_not_registered&email=" 
                                        + URLEncoder.encode(email, StandardCharsets.UTF_8));
                                } else {
                                    response.sendRedirect("/login?error=oauth_error");
                                }
                            });
                })
                .logout(logout -> logout
                    .logoutUrl("/logout") // URL для logout
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll()
                )
                .csrf(csrf -> {
                    csrf.ignoringRequestMatchers(
                        "/ws/**",
                        "/api/users/accept-terms",
                        "/api/chat/temporary"
                    );
                });
        return http.build();
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserCreate() {
        return userRequest -> {
            OidcUser oidcUser = new OidcUserService().loadUser(userRequest);
            String email = oidcUser.getEmail();
            User user = userService.findUser(email);
            if (user == null) {
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
                request.getSession().setAttribute("oauth2User", oidcUser);
                throw new UserNotRegisteredException(email);
            }

            Set<GrantedAuthority> authorities = new HashSet<>();

            // Добавляем роли
            for (Role role : user.getRoles()) {
                String roleKey = "ROLE_" + role.getName().toUpperCase();

                authorities.add(new SimpleGrantedAuthority(roleKey));

                logger.debug("Роль добалена: {}", roleKey);

                // Добавляем пермишены из роли
                for (Permission perm : role.getPermissions()) {

                    String permKey = perm.getName().toUpperCase();

                    logger.debug("Разрешение добалено: {}", permKey);

                    authorities.add(new SimpleGrantedAuthority(permKey));
                }
            }

            logger.debug("Итоговый список GrantedAuthority: {}", authorities);
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
        };
    }

    @Bean
    SavedRequestAwareAuthenticationSuccessHandler savedRequestAuthenticationSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/catalog");
        handler.setAlwaysUseDefaultTargetUrl(false);
        return handler;
    }

    @Bean
    public RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(DataSize.ofMegabytes(100));
        return factory.createMultipartConfig();
    }

    @Bean
    ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            SessionCookieConfig sessionCookieConfig = servletContext.getSessionCookieConfig();
            sessionCookieConfig.setMaxAge(14 * 24 * 60 * 60); // 14 дней в секундах
        };
    }
}