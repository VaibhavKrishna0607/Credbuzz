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
    /** Credit fairness: 1 - abs(proposed-base)/base, 0-1 */
    private Double creditFairness;
    /** Deadline realism: 0-1, penalizes aggressive timelines */
    private Double deadlineRealism;
    private Double completionRate;
    private Double avgRating;
    private Double lateRatio;
    private Double workloadScore;
    /** Experience level normalized 0-1 for ML (log scale) */
    private Double experienceLevel;
    
    private Double proposalRelevanceScore;
    private Double keywordCoverageScore;

    private static final double LOG10_100 = 2.0; // log10(100)=2 for normalization

    /**
     * Create from feature snapshot. All features sent to ML are 0-1 normalized.
     */
    public static MLPredictionRequest fromSnapshot(BidFeatureSnapshot snapshot) {
        Integer rawExp = snapshot.getExperienceLevel();
        int exp = rawExp != null ? rawExp : 0;
        double experienceLevelNorm = exp <= 0 ? 0.0 : Math.min(1.0, Math.log10(exp + 1) / LOG10_100);

        return MLPredictionRequest.builder()
                .skillMatchScore(nullToZero(snapshot.getSkillMatchScore(), 0.0))
                .creditFairness(snapshot.getCreditFairness() != null ? snapshot.getCreditFairness() :
                        (snapshot.getCreditDelta() != null ? Math.max(0, 1.0 - snapshot.getCreditDelta()) : 0.5))
                .deadlineRealism(snapshot.getDeadlineRealism() != null ? snapshot.getDeadlineRealism() :
                        (snapshot.getDeadlineDelta() != null ? snapshot.getDeadlineDelta() : 0.5))
                .completionRate(nullToZero(snapshot.getCompletionRate(), 0.6))
                .avgRating(nullToZero(snapshot.getAvgRating(), 0.5))
                .lateRatio(nullToZero(snapshot.getLateRatio(), 0.1))
                .workloadScore(nullToZero(snapshot.getWorkloadScore(), 0.0))
                .experienceLevel(experienceLevelNorm)
                .proposalRelevanceScore(nullToZero(snapshot.getProposalRelevanceScore(), 0.5))
                .keywordCoverageScore(nullToZero(snapshot.getKeywordCoverageScore(), 0.5))
                .build();
    }

    private static double nullToZero(Double v, double fallback) {
        return v != null ? v : fallback;
    }
}
