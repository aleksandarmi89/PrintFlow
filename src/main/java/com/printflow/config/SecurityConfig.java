package com.printflow.config;

import com.printflow.entity.User.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity // Ovo će automatski napraviti konstruktor za final polja
public class SecurityConfig {
    
    // Spring će sam pronaći tvoj UserService (ili UserDetailsService) i PasswordEncoder
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		super();
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
	}

	@Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService); // Bez zagrada, koristimo polje
        authProvider.setPasswordEncoder(passwordEncoder);       // Bez zagrada, koristimo polje
        return authProvider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/public/**", "/static/**", "/css/**", 
                    "/js/**", "/images/**", "/webjars/**", 
                    "/favicon.ico", "/error", "/login", "/register"
                ).permitAll()
                
                .requestMatchers("/admin/**").hasAuthority(Role.ADMIN.name())
                
                .requestMatchers("/worker/**").hasAnyAuthority(
                    Role.WORKER_DESIGN.name(),
                    Role.WORKER_PRINT.name(),
                    Role.WORKER_GENERAL.name(),
                    Role.ADMIN.name()
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
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/access-denied")
            )
            .authenticationProvider(authenticationProvider());
        
        return http.build();
    }
    
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            String redirectUrl = "/";
            
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.name()))) {
                redirectUrl = "/admin/dashboard";
            } else if (authorities.stream().anyMatch(a -> 
                a.getAuthority().equals(Role.WORKER_DESIGN.name()) ||
                a.getAuthority().equals(Role.WORKER_PRINT.name()) ||
                a.getAuthority().equals(Role.WORKER_GENERAL.name()))) {
                redirectUrl = "/worker/dashboard";
            }
            
            response.sendRedirect(request.getContextPath() + redirectUrl);
        };
    }
}