package lk.finora.api.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.common.error.ErrorResponse;
import lk.finora.api.security.JwtAuthFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless API security. Public: health, dev-only OpenAPI docs and /api/v1/auth/**.
 * Everything else needs a valid access token (checked by JwtAuthFilter). Role rules come in T06.
 */
@Configuration
public class SecurityConfig {

    private final JsonMapper jsonMapper;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JsonMapper jsonMapper, JwtAuthFilter jwtAuthFilter) {
        this.jsonMapper = jsonMapper;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /** BCrypt with cost 12 (decision D09). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // Clients are the Android app only: no browser origins, no cookies/sessions
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Only served when springdoc.api-docs.enabled=true (dev profile)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(req, res, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, e) ->
                                write(req, res, HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access denied")));
        return http.build();
    }

    private void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code,
            String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(res.getOutputStream(),
                ErrorResponse.of(status.value(), code, message, req.getRequestURI()));
    }
}
