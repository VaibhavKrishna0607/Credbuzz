package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================
 * LEARNING NOTE: Entity Class (JPA)
 * ============================================
 * 
 * This is equivalent to your Mongoose User schema.
 * 
 * COMPARISON:
 * -----------------------------------------
 * Mongoose:
 * const userSchema = new mongoose.Schema({
 *   name: { type: String, required: true },
 *   email: { type: String, required: true, unique: true },
 *   ...
 * });
 * 
 * JPA Entity:
 * @Entity
 * public class User {
 *   @Column(nullable = false)
 *   private String name;
 *   
 *   @Column(nullable = false, unique = true)
 *   private String email;
 *   ...
 * }
 * -----------------------------------------
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(length = 500)
    private String bio;

    @ElementCollection
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Column
    private String avatar;

    @Column
    private Integer credits;

    @Column
    private String googleId;

    @Column
    private String otp;

    @Column
    private LocalDateTime otpExpires;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (credits == null) {
            credits = 50; // Default starting credits
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
