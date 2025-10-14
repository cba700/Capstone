package com.capstone.global.config;

import com.capstone.domain.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**") // '/api/'로 시작하는 모든 경로는 CSRF 보호 예외
            )
            .authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**", "/api/gemini/**").permitAll()
                    .requestMatchers("/api/sessions/**", "/api/themes/**", "/api/storybooks/**").authenticated()
                    .requestMatchers("/main").authenticated()
                    .anyRequest().permitAll()
            )
            .formLogin(formLogin ->
                formLogin
                    .loginPage("/login")
                    .defaultSuccessUrl("/main", true)
                    .usernameParameter("email")
                    .permitAll()
            )
            .logout(logout ->
                logout
                    .logoutSuccessUrl("/login")
                    .permitAll()
            )
            .userDetailsService(userDetailsService);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
