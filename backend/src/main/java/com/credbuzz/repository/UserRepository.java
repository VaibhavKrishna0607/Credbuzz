package com.credbuzz.repository;

import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================
 * LEARNING NOTE: Repository Interface
 * ============================================
 * 
 * Repositories are like your Mongoose model methods.
 * JpaRepository provides CRUD operations automatically!
 * 
 * COMPARISON:
 * -----------------------------------------
 * Mongoose:
 * const user = await User.findOne({ email });
 * const user = await User.findById(id);
 * await user.save();
 * 
 * JPA Repository:
 * Optional<User> user = userRepository.findByEmail(email);
 * Optional<User> user = userRepository.findById(id);
 * userRepository.save(user);
 * -----------------------------------------
 * 
 * Spring Data JPA automatically implements these methods based on naming!
 * findByEmail -> SELECT * FROM users WHERE email = ?
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by email
     * Spring automatically creates: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);
    
    /**
     * Find user by Google ID (for OAuth)
     */
    Optional<User> findByGoogleId(String googleId);
}
