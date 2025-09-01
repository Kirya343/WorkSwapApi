package org.workswap.api.config;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workswap.core.exceptions.UserNotRegisteredException;
import org.workswap.core.services.UserService;
import org.workswap.core.services.components.security.CustomOAuth2SuccessHandler;
import org.workswap.core.services.components.security.RolesPermissionsConverter;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.user.Permission;
import org.workswap.datasource.central.model.user.Role;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    private final RolesPermissionsConverter converter;
    private final UserService userService;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/**",
                    "/oauth2/**", 
                    "/login/**"
                ).permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers( "/app/**").authenticated()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2.successHandler(customOAuth2SuccessHandler))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
            )
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .build();
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
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(DataSize.ofMegabytes(100));
        return factory.createMultipartConfig();
    }
}