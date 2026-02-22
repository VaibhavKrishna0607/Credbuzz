package com.credbuzz.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ============================================
 * LEARNING NOTE: JWT Authentication Filter
 * ============================================
 * 
 * This is equivalent to your protect middleware in auth.js:
 * 
 * exports.protect = async (req, res, next) => {
 *   let token;
 *   if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
 *     token = req.headers.authorization.split(' ')[1];
 *   }
 *   ...
 * };
 * 
 * In Spring Boot, we use a Filter that runs on every request.
 * 
 * FLOW:
 * 1. Extract token from Authorization header
 * 2. Validate token
 * 3. Load user from database
 * 4. Set authentication in SecurityContext (like req.user in Express)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Get Authorization header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // Check if header exists and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token (remove "Bearer " prefix)
        // In Node.js: token = req.headers.authorization.split(' ')[1];
        jwt = authHeader.substring(7);

        try {
            // Extract email from token
            userEmail = jwtService.extractUsername(jwt);

            // If email found and not already authenticated
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // Load user from database
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // Validate token
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    // Set authentication in context (like req.user = user in Express)
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token invalid - continue without authentication
            // Protected routes will fail, public routes will work
            logger.error("JWT validation error: " + e.getMessage());
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }
}
