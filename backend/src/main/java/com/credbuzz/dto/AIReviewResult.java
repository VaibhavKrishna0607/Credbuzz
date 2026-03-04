package com.credbuzz.dto;

import lombok.*;

/**
 * AI Review Result DTO - Contains AI assessment of a submission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIReviewResult {
    
    /**
     * How well the submission covers original task requirements (0-100)
     */
    private Double requirementCoverage;
    
    /**
     * How aligned the work is with the bidder's original proposal (0-100)
     */
    private Double proposalAlignment;
    
    /**
     * Technical quality assessment (0-100)
     */
    private Double technicalScore;
    
    /**
     * Deadline compliance (0 = very late, 100 = on-time or early)
     */
    private Double deadlineCompliance;
    
    /**
     * Combined final AI compliance score (weighted average)
     */
    private Double finalScore;
    
    /**
     * Detailed AI analysis/feedback
     */
    private String analysis;
    
    /**
     * Flagged concerns (if any)
     */
    private String concerns;
    
    /**
     * Whether the submission passes minimum quality threshold
     */
    private Boolean passesQualityThreshold;
    
    /**
     * Minimum quality threshold used
     */
    private Double qualityThreshold;
}
