package com.credbuzz.security;

import com.credbuzz.entity.User;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * ============================================
 * LEARNING NOTE: UserDetailsService
 * ============================================
 * 
 * Spring Security needs to know how to load users from your database.
 * UserDetailsService is the interface for that.
 * 
 * This is called automatically when:
 * 1. A user tries to login
 * 2. JWT filter needs to validate a token
 * 
 * In Express, you did this manually:
 * const user = await User.findOne({ email }).select('+password');
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Load user by username (we use email as username)
     * 
     * This is called by Spring Security during authentication
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        /**
         * Return Spring Security's UserDetails object
         * 
         * This wraps your User entity in Spring's format:
         * - username (we use email)
         * - password
         * - authorities (roles/permissions - we're not using these yet)
         */
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword() != null ? user.getPassword() : "",
                new ArrayList<>()  // Empty authorities for now, can add roles later
        );
    }
}
