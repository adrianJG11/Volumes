package com.adrian.volumes.user;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import java.io.IOException;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    UserDetailsService usersForLogin(UserRepository repository) {
        return email -> repository.findLoginByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(access -> access
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/csrf", "/books", "/books/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .anyRequest().authenticated())
            .requestCache(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> unauthorized(response))
                .accessDeniedHandler((request, response, exception) -> forbidden(response)))
            .formLogin(login -> login.loginPage("/auth/login").loginProcessingUrl("/auth/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Invalid email or password.\"}");
                }))
            .logout(logout -> logout.logoutUrl("/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        // Keep Spring Security's default session-based CSRF protection and session fixation defense.
        return http.build();
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Authentication required.\"}");
    }

    private static void forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"Request not permitted. Check authentication and CSRF token.\"}");
    }
}
