package com.flagship.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The REST API: HTTP Basic login, no session, no CSRF token (there is no browser session to attack).
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, ApiAuthorization authorization) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests.anyRequest().access(authorization));
        return http.build();
    }

    /**
     * The admin web pages: a login form and a session. CSRF protection stays ON here,
     * because a browser session can be abused by other websites. Everything except the
     * login page needs a login; what a person may do inside a project is checked per page.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http.formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/projects", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}