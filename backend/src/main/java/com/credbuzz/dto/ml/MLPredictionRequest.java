package com.credbuzz.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for ML prediction service
 * Sent to POST http://localhost:8000/predict
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionRequest {
    
    private Double skillMatchScore;
    private Double creditDelta;
    private Double deadlineDelta;
    private Double completionRate;
    private Double avgRating;
    private Double lateRatio;
    private Double workloadScore;
    private Double experienceLevel;
    
    // New text-based features
    private Double proposalRelevanceScore;
    private Double keywordCoverageScore;

    /**
     * Create from feature snapshot
     */
    public static MLPredictionRequest fromSnapshot(BidFeatureSnapshot snapshot) {
        return MLPredictionRequest.builder()
                .skillMatchScore(snapshot.getSkillMatchScore())
                .creditDelta(snapshot.getCreditDelta())
                .deadlineDelta(snapshot.getDeadlineDelta())
                .completionRate(snapshot.getCompletionRate())
                .avgRating(snapshot.getAvgRating())
                .lateRatio(snapshot.getLateRatio())
                .workloadScore(snapshot.getWorkloadScore())
                .experienceLevel(snapshot.getExperienceLevel() != null ? 
                        snapshot.getExperienceLevel().doubleValue() : 0.0)
                // New text-based features
                .proposalRelevanceScore(snapshot.getProposalRelevanceScore() != null ?
                        snapshot.getProposalRelevanceScore() : 0.5)
                .keywordCoverageScore(snapshot.getKeywordCoverageScore() != null ?
                        snapshot.getKeywordCoverageScore() : 0.5)
                .build();
    }
}
