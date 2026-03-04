package com.credbuzz.security;

import com.credbuzz.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ============================================
 * LEARNING NOTE: JWT Service
 * ============================================
 * 
 * This is equivalent to your generateToken() function in authController.js
 * 
 * In Node.js:
 * const generateToken = (id) => {
 *   return jwt.sign({ id }, process.env.JWT_SECRET, {
 *     expiresIn: process.env.JWT_EXPIRE
 *   });
 * };
 * 
 * In Spring Boot, we encapsulate this logic in a service class.
 */
@Service
public class JwtService {

    /**
     * @Value injects values from application.properties
     * This is like process.env.JWT_SECRET in Node.js
     */
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Extract username (email) from token
     * 
     * In Node.js: jwt.verify(token, secret).id
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID from token
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    /**
     * Extract a specific claim from token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generate token for a user
     * 
     * In Node.js:
     * jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: '30d' });
     */
    public String generateToken(User user) {
        return generateToken(new HashMap<>(), user);
    }

    /**
     * Generate token with extra claims
     */
    public String generateToken(Map<String, Object> extraClaims, User user) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(user.getEmail())  // We use email as the subject
                .claim("userId", user.getId())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validate token
     * 
     * In Node.js (auth middleware):
     * const decoded = jwt.verify(token, process.env.JWT_SECRET);
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Check if token is expired
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extract expiration date from token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get signing key from secret
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
