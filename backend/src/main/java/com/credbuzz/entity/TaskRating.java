package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * TaskRating Entity - Stores creator's subjective rating of completed work.
 * 
 * Rating Components (1-5 scale each):
 * - Quality: How well the work met expectations
 * - Communication: Responsiveness and clarity during task
 * - Professionalism: Conduct and work ethic
 * 
 * Also stores:
 * - Written feedback
 * - AI score at time of rating (for divergence detection)
 */
@Entity
@Table(name = "task_ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater; // The creator giving the rating

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private User ratedUser; // The bidder receiving the rating

    /**
     * Quality rating (1-5)
     */
    @Column(nullable = false)
    private Integer qualityScore;

    /**
     * Communication rating (1-5)
     */
    @Column(nullable = false)
    private Integer communicationScore;

    /**
     * Professionalism rating (1-5)
     */
    @Column(nullable = false)
    private Integer professionalismScore;

    /**
     * Average of the three ratings
     */
    @Column(nullable = false)
    private Double averageScore;

    /**
     * Written feedback (optional)
     */
    @Column(length = 2000)
    private String feedback;

    /**
     * AI score at the time rating was given (for divergence detection)
     */
    @Column
    private Double aiScoreSnapshot;

    /**
     * Divergence between creator rating and AI score.
     * Calculated as: |averageScore * 20 - aiScoreSnapshot|
     * Used for abuse detection.
     */
    @Column
    private Double scoreDivergence;

    /**
     * Whether this rating may be suspicious (high divergence)
     */
    @Column
    private Boolean flaggedForReview;

    /**
     * Timestamps
     */
    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateAverageScore();
        calculateDivergence();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateAverageScore();
        calculateDivergence();
    }

    private void calculateAverageScore() {
        if (qualityScore != null && communicationScore != null && professionalismScore != null) {
            averageScore = (qualityScore + communicationScore + professionalismScore) / 3.0;
        }
    }

    private void calculateDivergence() {
        if (averageScore != null && aiScoreSnapshot != null) {
            // Convert average (1-5) to (0-100) scale for comparison
            double creatorScoreNormalized = averageScore * 20;
            scoreDivergence = Math.abs(creatorScoreNormalized - aiScoreSnapshot);
            // Flag if divergence exceeds 25 points (significant disagreement)
            flaggedForReview = scoreDivergence > 25.0;
        }
    }
}
