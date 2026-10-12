package aitu.network.aitunetwork.config;

import aitu.network.aitunetwork.config.security.CustomAuthenticationEntryPoint;
import aitu.network.aitunetwork.config.security.CustomJwtFilter;
import aitu.network.aitunetwork.config.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomUserDetailsService userDetailsService;
    private final CustomJwtFilter jwtFilter;
    @Value("${secure-talk.cors.allowed-origins}")
    private List<String> allowedOrigins;
    private final static String[] ALLOW_ALL_MATCHER = new String[]{
            "/swagger-ui/**",
            "/swagger-resources/*",
            "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/ws/**",
            "/api/v1/chats/users/**",
            "/api/v1/users/public/**",
            "/api/v1/file/**",
            "/api/v1/post/public/**",
            "/api/v1/comments/public/**",
            "/error",
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CustomAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
//        if (isLocal()){
//            http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/**").permitAll());
//        }
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize ->
                        authorize.requestMatchers(ALLOW_ALL_MATCHER).permitAll()
                                .anyRequest().authenticated()
                )
                .logout(logout -> {
                    logout.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler());
                    logout.logoutUrl("/api/v1/auth/logout");
                    logout.deleteCookies("jwt");
                    logout.addLogoutHandler(
                            new HeaderWriterLogoutHandler(
                                    new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.COOKIES)
                            )
                    );
                })
                .sessionManagement(manager ->
                        manager.sessionCreationPolicy(STATELESS)
                )
                .exceptionHandling(cus ->
                        cus.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .anonymous(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authProvider);
    }
}
