package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ============================================
 * Auction History Entity
 * ============================================
 * 
 * Stores historical data for each auction.
 * Used for ML training and analytics.
 * 
 * Each record represents a bid in a completed auction,
 * with all the features needed for ML model training.
 */
@Entity
@Table(name = "auction_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to original entities (nullable if deleted)
    @Column
    private Long taskId;

    @Column
    private Long bidId;

    @Column
    private Long bidderId;

    @Column
    private Long posterId;

    // Task info snapshot
    @Column(nullable = false)
    private String taskTitle;

    @Column
    private String taskCategory;

    @Column(nullable = false)
    private Integer originalCredits; // Credits offered by poster

    @Column
    private Integer taskSkillCount;

    // Bid info snapshot  
    @Column(nullable = false)
    private Integer proposedCredits;

    @Column(nullable = false)
    private Integer proposedCompletionDays;

    // ================================
    // FEATURES FOR ML MODEL
    // ================================

    // Skill match (0.0 - 1.0)
    @Column(nullable = false)
    private Double skillMatchScore;

    // Historical completion rate (0.0 - 1.0)
    @Column(nullable = false)
    private Double completionRate;

    // Credit delta (proposed - original) / original
    @Column(nullable = false)
    private Double creditDelta;

    // Deadline realism (proposed days / typical days for similar tasks)
    @Column(nullable = false)
    private Double deadlineDelta;

    // User's average rating (0.0 - 5.0)
    @Column(nullable = false)
    private Double avgRating;

    // Current workload score (0.0 - 1.0, lower is better)
    @Column(nullable = false)
    private Double workloadScore;

    // On-time completion rate (0.0 - 1.0)
    @Column(nullable = false)
    private Double onTimeRate;

    // Bid win rate (0.0 - 1.0)
    @Column(nullable = false)
    private Double bidWinRate;

    // Total score calculated by heuristic
    @Column(nullable = false)
    private Double heuristicScore;

    // ================================
    // ML FEATURE SNAPSHOT (JSON)
    // ================================
    
    /**
     * Complete feature snapshot as JSON for ML training.
     * Stored as JSON to ensure immutability and flexibility.
     * Contains all BidFeatureSnapshot fields at auction close time.
     */
    @Column(columnDefinition = "TEXT")
    private String featureSnapshotJson;
    
    // ML prediction info (if used)
    @Column
    private Double mlPredictedScore;
    
    @Column
    @Builder.Default
    private Boolean usedMlPrediction = false;

    // ================================
    // OUTCOME (TARGET VARIABLE)
    // ================================

    @Column(nullable = false)
    @Builder.Default
    private Boolean wasSelected = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completedSuccessfully = false;

    @Column
    private Boolean wasOnTime;

    @Column
    private Integer actualCompletionDays;

    @Column
    private Double posterRating; // Rating given by poster after completion

    // Timestamps
    @Column(nullable = false)
    private LocalDateTime auctionClosedAt;

    @Column
    private LocalDateTime taskCompletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (auctionClosedAt == null) {
            auctionClosedAt = LocalDateTime.now();
        }
    }
}
