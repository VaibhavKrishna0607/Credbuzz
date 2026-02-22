package com.credbuzz.service;

import com.credbuzz.dto.RegisterRequest;
import com.credbuzz.dto.UserDto;
import com.credbuzz.entity.User;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * ============================================
 * LEARNING NOTE: Service Layer
 * ============================================
 * 
 * Services contain business logic.
 * This is where you put the code that was in your Express controllers.
 * 
 * In Express, your controller did everything:
 * exports.register = async (req, res) => {
 *   // validate, create user, return response
 * };
 * 
 * In Spring Boot, we separate concerns:
 * - Controller: Handle HTTP request/response
 * - Service: Business logic
 * - Repository: Database access
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user
     */
    public User register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create new user
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .credits(50) // Starting credits
                .build();

        return userRepository.save(user);
    }

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Find all users
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Update user profile
     */
    public User updateProfile(Long userId, String name, String bio, List<String> skills) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (name != null) {
            user.setName(name);
        }
        if (bio != null) {
            user.setBio(bio);
        }
        if (skills != null) {
            user.setSkills(skills);
        }

        return userRepository.save(user);
    }

    /**
     * Add credits to user
     */
    public User addCredits(Long userId, int amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setCredits(user.getCredits() + amount);
        return userRepository.save(user);
    }

    /**
     * Deduct credits from user
     */
    public User deductCredits(Long userId, int amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getCredits() < amount) {
            throw new RuntimeException("Insufficient credits");
        }

        user.setCredits(user.getCredits() - amount);
        return userRepository.save(user);
    }

    /**
     * Convert User entity to DTO
     */
    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bio(user.getBio())
                .skills(user.getSkills())
                .avatar(user.getAvatar())
                .credits(user.getCredits())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Save user
     */
    public User save(User user) {
        return userRepository.save(user);
    }
}
