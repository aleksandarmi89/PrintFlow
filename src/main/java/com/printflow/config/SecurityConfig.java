package com.printflow.config;

import com.printflow.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity // Ovo će automatski napraviti konstruktor za final polja
public class SecurityConfig {
    
    // Spring će sam pronaći tvoj UserService (ili UserDetailsService) i PasswordEncoder
    private final UserDetailsService userDetailsService;
    private final boolean corsEnabled;
    private final List<String> corsAllowedOrigins;
    private final boolean hstsEnabled;

    public SecurityConfig(UserDetailsService userDetailsService,
                          @Value("${app.security.cors.enabled:false}") boolean corsEnabled,
                          @Value("${app.security.cors.allowed-origins:}") List<String> corsAllowedOrigins,
                          @Value("${app.security.hsts.enabled:false}") boolean hstsEnabled) {
		super();
		this.userDetailsService = userDetailsService;
        this.corsEnabled = corsEnabled;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.hstsEnabled = hstsEnabled;
	}

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/webhooks/stripe")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/public/**", "/static/**", "/css/**", 
                    "/js/**", "/images/**", "/webjars/**", 
                    "/favicon.ico", "/error", "/login", "/register", "/invite/**", "/portal/**", "/p/**",
                    "/forgot-password", "/reset-password"
                ).permitAll()
                .requestMatchers("/webhooks/stripe").permitAll()
                
                .requestMatchers("/admin/companies/**").hasAuthority(User.Role.SUPER_ADMIN.name())
                .requestMatchers("/admin/audit-logs/**").hasAuthority(User.Role.SUPER_ADMIN.name())
                .requestMatchers("/admin/rate-limit/**").hasAuthority(User.Role.SUPER_ADMIN.name())
                .requestMatchers("/admin/billing/**").hasAnyAuthority(User.Role.ADMIN.name(), User.Role.SUPER_ADMIN.name())
                .requestMatchers("/settings/**").hasAuthority(User.Role.ADMIN.name())
                .requestMatchers("/products/**").hasAnyAuthority(User.Role.ADMIN.name(), User.Role.MANAGER.name(), User.Role.SUPER_ADMIN.name())
                .requestMatchers("/pricing/**").hasAnyAuthority(User.Role.ADMIN.name(), User.Role.MANAGER.name(), User.Role.SUPER_ADMIN.name())
                .requestMatchers("/admin/**").hasAnyAuthority(User.Role.ADMIN.name(), User.Role.MANAGER.name(), User.Role.SUPER_ADMIN.name())
                
                .requestMatchers("/worker/**").hasAnyAuthority(
                    User.Role.WORKER_DESIGN.name(),
                    User.Role.WORKER_PRINT.name(),
                    User.Role.WORKER_GENERAL.name(),
                    User.Role.ADMIN.name(),
                    User.Role.SUPER_ADMIN.name()
                )
                
                .requestMatchers("/api/files/download/**").authenticated()
                .requestMatchers("/api/files/thumbnail/**").permitAll()
                
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/access-denied")
            )
            .sessionManagement(session -> session.sessionFixation().migrateSession())
            .headers(headers -> {
                headers.frameOptions(frame -> frame.sameOrigin());
                headers.contentTypeOptions(cto -> {});
                headers.referrerPolicy(ref -> ref.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicyHeader(pp -> pp.policy("geolocation=(), microphone=(), camera=()"));
                if (hstsEnabled) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31536000));
                } else {
                    headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                }
            })
            .userDetailsService(userDetailsService);
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowedOrigins = corsAllowedOrigins == null
            ? List.of()
            : corsAllowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        if (!corsEnabled || allowedOrigins.isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            String redirectUrl = "/";
            
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals(User.Role.SUPER_ADMIN.name()))) {
                redirectUrl = "/admin/companies";
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals(User.Role.ADMIN.name()))) {
                redirectUrl = "/admin/dashboard";
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals(User.Role.MANAGER.name()))) {
                redirectUrl = "/admin/dashboard";
            } else if (authorities.stream().anyMatch(a -> 
                a.getAuthority().equals(User.Role.WORKER_DESIGN.name()) ||
                a.getAuthority().equals(User.Role.WORKER_PRINT.name()) ||
                a.getAuthority().equals(User.Role.WORKER_GENERAL.name()))) {
                redirectUrl = "/worker/dashboard";
            }
            
            response.sendRedirect(request.getContextPath() + redirectUrl);
        };
    }
}
