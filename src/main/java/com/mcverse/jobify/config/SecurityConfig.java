package com.mcverse.jobify.config;

import com.mcverse.jobify.auth.security.AuthRateLimitFilter;
import com.mcverse.jobify.auth.security.JwtAuthFilter;
import com.mcverse.jobify.auth.security.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private SecurityErrorHandler securityErrorHandler;

    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /** The H2 console is a database admin UI: reachable only where it is switched on (dev). */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /** Swagger UI and the OpenAPI document: switched off in the postgres profile unless app.docs.enabled=true. */
    @Value("${app.docs.enabled:true}")
    private boolean docsEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/jobs/mine").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.GET, "/jobs/saved").hasRole("SEEKER")
                        .requestMatchers(HttpMethod.GET, "/companies/*", "/companies/*/logo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs/*/images/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs", "/jobs/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/content").permitAll()
                        .requestMatchers("/auth/**", "/actuator/health").permitAll()
                        .requestMatchers(optionalPublicPaths()).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .headers(headers -> headers.frameOptions(
                        frameOptions -> frameOptions.sameOrigin()
                ))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private String[] optionalPublicPaths() {
        List<String> paths = new java.util.ArrayList<>();
        if (h2ConsoleEnabled) paths.add("/h2-console/**");
        if (docsEnabled) paths.addAll(List.of("/swagger-ui/**", "/v3/api-docs/**"));
        // an empty matcher list is rejected, so keep one path that nothing serves
        if (paths.isEmpty()) paths.add("/__none__");
        return paths.toArray(String[]::new);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // browsers only let scripts read these response headers when they are exposed
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
