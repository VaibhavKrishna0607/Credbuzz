package com.credbuzz.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * ============================================
 * LEARNING NOTE: Security Configuration
 * ============================================
 * 
 * This configures Spring Security for your application.
 * It defines:
 * - Which endpoints are public vs protected
 * - How authentication works
 * - CORS settings
 * - Password encoding
 * 
 * COMPARISON WITH EXPRESS:
 * In Express, you manually add middleware:
 * router.post('/', protect, createTask);  // protected
 * router.get('/', getTasks);              // public
 * 
 * In Spring Security, we configure URL patterns:
 * .requestMatchers("/api/tasks").permitAll()  // public
 * .requestMatchers("/api/tasks/**").authenticated()  // protected
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Security Filter Chain - defines security rules
     * 
     * Think of this as defining your route-level middleware
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (not needed for stateless API with JWT)
            .csrf(csrf -> csrf.disable())
            
            // Enable CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // ============================================
                // PUBLIC ROUTES (no authentication required)
                // ============================================
                
                // Auth routes
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/google").permitAll()
                .requestMatchers("/api/auth/request-otp", "/api/auth/reset-password").permitAll()
                
                // Public task routes (GET only)
                .requestMatchers(HttpMethod.GET, "/api/tasks").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tasks/available").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tasks/autocomplete-*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tasks/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tasks/{id}/bids").permitAll()
                
                // Public user routes
                .requestMatchers(HttpMethod.GET, "/api/users").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/user-stats").permitAll()
                
                // H2 Console (for development)
                .requestMatchers("/h2-console/**").permitAll()
                
                // Static files
                .requestMatchers("/uploads/**").permitAll()
                
                // Admin routes (TODO: Add ADMIN role check in production)
                // For now, require authentication
                .requestMatchers("/api/admin/**").authenticated()
                
                // ============================================
                // PROTECTED ROUTES (authentication required)
                // ============================================
                
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            
            // Return 401 (not 403) for unauthenticated API requests
            .exceptionHandling(exc -> exc
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized - please login again\"}");
                })
            )

            // Stateless session (no server-side sessions - we use JWT)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Add JWT filter before Spring's auth filter
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Allow H2 console frames
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    /**
     * CORS Configuration
     * 
     * This is like your cors() middleware in Express:
     * app.use(cors({
     *   origin: ['http://localhost:5173', 'http://localhost:3000'],
     *   credentials: true
     * }));
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (frontend URLs)
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",   // Next.js
            "http://localhost:5173",   // Vite
            "https://credbuzz.netlify.app"  // Production
        ));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Password Encoder
     * 
     * BCrypt is the same algorithm you used in Node.js with bcryptjs
     * 
     * In Node.js:
     * const salt = await bcrypt.genSalt(10);
     * this.password = await bcrypt.hash(this.password, salt);
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication Provider
     * 
     * Tells Spring how to authenticate users (using our UserDetailsService)
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication Manager
     * 
     * Used in AuthController for login
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
