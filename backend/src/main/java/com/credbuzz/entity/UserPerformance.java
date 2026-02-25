package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ============================================
 * User Performance Entity
 * ============================================
 * 
 * Tracks user performance metrics for bid evaluation.
 * Used by BidEvaluationService to calculate scores.
 * This data will be used to train ML models later.
 */
@Entity
@Table(name = "user_performance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPerformance {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // Task completion metrics
    @Column(nullable = false)
    @Builder.Default
    private Integer tasksCompleted = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer tasksAssigned = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer tasksAbandoned = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer tasksRejected = 0;  // Submissions rejected by poster

    // Completion rate = tasksCompleted / tasksAssigned (calculated)
    
    // Time performance
    @Column(nullable = false)
    @Builder.Default
    private Integer onTimeCompletions = 0;  // Completed before deadline

    @Column(nullable = false)
    @Builder.Default
    private Integer lateCompletions = 0;    // Completed after deadline

    @Column(nullable = false)
    @Builder.Default
    private Double avgCompletionDays = 0.0; // Average days to complete

    // Ratings (1-5 scale)
    @Column(nullable = false)
    @Builder.Default
    private Double avgRating = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalRatings = 0;

    // Current workload
    @Column(nullable = false)
    @Builder.Default
    private Integer activeTasksCount = 0;

    // Bidding metrics
    @Column(nullable = false)
    @Builder.Default
    private Integer totalBidsPlaced = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer bidsWon = 0;

    @Column(nullable = false)
    @Builder.Default
    private Double avgBidCredits = 0.0;

    // Skill verification
    @Column(nullable = false)
    @Builder.Default
    private Integer skillVerificationScore = 0; // 0-100

    @Column
    private LocalDateTime lastActiveAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Calculated metrics
    public double getCompletionRate() {
        if (tasksAssigned == 0) return 0.0;
        return (double) tasksCompleted / tasksAssigned;
    }

    public double getOnTimeRate() {
        int totalCompleted = onTimeCompletions + lateCompletions;
        if (totalCompleted == 0) return 1.0; // New users get benefit of doubt
        return (double) onTimeCompletions / totalCompleted;
    }

    public double getBidWinRate() {
        if (totalBidsPlaced == 0) return 0.0;
        return (double) bidsWon / totalBidsPlaced;
    }
}
