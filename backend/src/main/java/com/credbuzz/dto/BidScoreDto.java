package com.credbuzz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bid evaluation scores
 * Contains all scoring components used to rank bids
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidScoreDto {
    
    private Long bidId;
    private Long bidderId;
    private String bidderName;
    
    // Individual score components (0.0 - 1.0)
    private Double skillMatchScore;
    private Double completionRate;
    private Double creditFairnessScore;
    private Double deadlineRealism;
    private Double ratingScore;
    private Double workloadScore;
    private Double onTimeScore;
    private Double bidWinRate;
    
    // Weighted final score
    private Double totalScore;
    
    // Ranking
    private Integer rank;
    
    // Original bid details
    private Integer proposedCredits;
    private Integer proposedCompletionDays;
    private String proposalMessage;
    
    // ML prediction info
    private Boolean usedMlPrediction;
    private Double mlConfidence;
}
