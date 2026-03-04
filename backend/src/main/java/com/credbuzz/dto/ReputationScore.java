package com.credbuzz.dto;

import lombok.*;

/**
 * Reputation Score DTO - Contains breakdown of a user's reputation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReputationScore {
    
    /**
     * User ID
     */
    private Long userId;
    
    /**
     * User name
     */
    private String userName;
    
    // ============================================
    // Component Scores (0-100 scale)
    // ============================================
    
    /**
     * Average AI compliance score across all tasks
     */
    private Double aiComplianceScore;
    
    /**
     * Average creator satisfaction score (from ratings)
     */
    private Double creatorSatisfactionScore;
    
    /**
     * Reliability score (completion rate, timeliness, dispute history)
     */
    private Double reliabilityScore;
    
    // ============================================
    // Final Score
    // ============================================
    
    /**
     * Final weighted reputation score (0-100)
     * Formula: 0.4*AI + 0.3*Creator + 0.3*Reliability
     */
    private Double finalScore;
    
    /**
     * Reputation tier based on final score
     */
    private ReputationTier tier;
    
    // ============================================
    // Weight Adjustments
    // ============================================
    
    /**
     * Applied AI score weight (default 0.4)
     */
    private Double aiWeight;
    
    /**
     * Applied creator score weight (default 0.3, reduced if abuse detected)
     */
    private Double creatorWeight;
    
    /**
     * Applied reliability weight (default 0.3)
     */
    private Double reliabilityWeight;
    
    /**
     * Whether creator weight was reduced due to divergence
     */
    private Boolean creatorWeightReduced;
    
    /**
     * Average divergence between AI and creator scores
     */
    private Double averageDivergence;
    
    // ============================================
    // Statistics
    // ============================================
    
    /**
     * Total completed tasks
     */
    private Integer completedTasks;
    
    /**
     * Total ratings received
     */
    private Integer totalRatings;
    
    /**
     * Task completion rate (completed / assigned)
     */
    private Double completionRate;
    
    /**
     * On-time delivery rate
     */
    private Double onTimeRate;
    
    /**
     * Dispute loss rate
     */
    private Double disputeLossRate;
    
    /**
     * Reputation tier enum
     */
    public enum ReputationTier {
        NEWCOMER("Newcomer", 0),
        BRONZE("Bronze", 40),
        SILVER("Silver", 60),
        GOLD("Gold", 75),
        PLATINUM("Platinum", 85),
        DIAMOND("Diamond", 95);
        
        private final String displayName;
        private final int minScore;
        
        ReputationTier(String displayName, int minScore) {
            this.displayName = displayName;
            this.minScore = minScore;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getMinScore() {
            return minScore;
        }
        
        public static ReputationTier fromScore(double score, int completedTasks) {
            if (completedTasks < 3) return NEWCOMER;
            if (score >= 95) return DIAMOND;
            if (score >= 85) return PLATINUM;
            if (score >= 75) return GOLD;
            if (score >= 60) return SILVER;
            if (score >= 40) return BRONZE;
            return NEWCOMER;
        }
    }
}
