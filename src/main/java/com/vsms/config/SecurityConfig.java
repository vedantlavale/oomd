package com.vsms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everything except the login page and static files needs a signed-in user.
 *
 * <p>Access is checked twice on purpose: here by URL (so a request from the wrong role is refused
 * before Spring even reads its parameters), and again with {@code @PreAuthorize} on each controller,
 * which documents the rule next to the code. A mechanic is further limited to the jobs assigned to
 * them by the service layer.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String ADVISOR = "SERVICE_ADVISOR";
    private static final String MECHANIC = "MECHANIC";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**",
                                "/webjars/**", "/favicon.ico").permitAll()

                        // 1. Administrator only (most specific rules first)
                        .requestMatchers("/users/**").hasRole(ADMIN)
                        .requestMatchers("/mechanics/new", "/mechanics/*/edit", "/catalog/new", "/catalog/*/edit",
                                "/payments/*/reverse").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/mechanics", "/mechanics/*", "/mechanics/*/deactivate",
                                "/mechanics/*/activate", "/catalog", "/catalog/*", "/catalog/*/active",
                                "/catalog/*/delete", "/customers/*/delete", "/vehicles/*/delete", "/parts/*/delete")
                        .hasRole(ADMIN)

                        // 2. Staff only: things a mechanic must never do
                        .requestMatchers("/jobs/new", "/jobs/*/edit", "/parts/new", "/parts/*/edit")
                        .hasAnyRole(ADMIN, ADVISOR)
                        .requestMatchers(HttpMethod.POST, "/jobs", "/jobs/*", "/jobs/*/assign", "/parts", "/parts/*",
                                "/parts/*/stock").hasAnyRole(ADMIN, ADVISOR)
                        .requestMatchers("/customers/**", "/vehicles/**", "/appointments/**", "/mechanics/**",
                                "/catalog/**", "/invoices/**", "/payments/**", "/reports/**")
                        .hasAnyRole(ADMIN, ADVISOR)

                        // 3. Everyone who works in the workshop (a mechanic is limited to their own jobs in the service layer)
                        .requestMatchers("/jobs/**", "/parts/**").hasAnyRole(ADMIN, ADVISOR, MECHANIC)
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());
        return http.build();
    }
}
