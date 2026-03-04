package com.credbuzz.service.ml;

import com.credbuzz.config.MLConfig;
import com.credbuzz.dto.ml.BidFeatureSnapshot;
import com.credbuzz.dto.ml.MLPredictionRequest;
import com.credbuzz.dto.ml.MLPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================
 * ML Integration Service
 * ============================================
 * 
 * Handles communication with the external ML prediction service.
 * Provides automatic fallback to heuristic scoring when ML is unavailable.
 * 
 * New Feature: Confidence-Aware Automation
 * - If top 2 bids are too close (difference < CONFIDENCE_THRESHOLD),
 *   marks result as requiring manual confirmation.
 * 
 * ML Service API:
 * POST /predict
 * Request: { "features": {...} }
 * Response: { "successProbability": 0.85, "confidence": 0.92 }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLIntegrationService {

    private final WebClient mlWebClient;
    private final MLConfig mlConfig;

    private static final int TIMEOUT_SECONDS = 5;
    
    /**
     * Confidence threshold for automatic assignment.
     * If (topScore - secondScore) < this threshold, require manual confirmation.
     */
    private static final double CONFIDENCE_THRESHOLD = 0.05;

    /**
     * Get predictions for all bids and rank them
     * Falls back to heuristic scoring if ML service is unavailable
     */
    public List<BidFeatureSnapshot> predictAndRankBids(List<BidFeatureSnapshot> features) {
        if (features == null || features.isEmpty()) {
            return new ArrayList<>();
        }

        // Try ML prediction if enabled
        if (mlConfig.isMlEnabled()) {
            try {
                List<BidFeatureSnapshot> rankedBids = getPredictionsFromML(features);
                if (rankedBids != null && !rankedBids.isEmpty()) {
                    log.info("Used ML predictions for {} bids", rankedBids.size());
                    return rankedBids;
                }
            } catch (Exception e) {
                log.warn("ML prediction failed, falling back to heuristic: {}", e.getMessage());
            }
        }

        // Fallback to heuristic ranking
        return rankByHeuristic(features);
    }

    /**
     * Call ML service to get predictions for all bids
     */
    private List<BidFeatureSnapshot> getPredictionsFromML(List<BidFeatureSnapshot> features) {
        List<BidFeatureSnapshot> results = new ArrayList<>();
        
        for (BidFeatureSnapshot feature : features) {
            try {
                MLPredictionResponse response = callMLService(feature);
                if (response != null) {
                    // Create updated snapshot with ML prediction
                    BidFeatureSnapshot updated = BidFeatureSnapshot.builder()
                            .bidId(feature.getBidId())
                            .bidderId(feature.getBidderId())
                            .taskId(feature.getTaskId())
                            .bidderName(feature.getBidderName())
                            .skillMatchScore(feature.getSkillMatchScore())
                            .creditDelta(feature.getCreditDelta())
                            .creditFairness(feature.getCreditFairness())
                            .deadlineDelta(feature.getDeadlineDelta())
                            .deadlineRealism(feature.getDeadlineRealism())
                            .proposedCredits(feature.getProposedCredits())
                            .proposedCompletionDays(feature.getProposedCompletionDays())
                            .completionRate(feature.getCompletionRate())
                            .avgRating(feature.getAvgRating())
                            .lateRatio(feature.getLateRatio())
                            .workloadScore(feature.getWorkloadScore())
                            .experienceLevel(feature.getExperienceLevel())
                            .onTimeRate(feature.getOnTimeRate())
                            .bidWinRate(feature.getBidWinRate())
                            .totalTasksAssigned(feature.getTotalTasksAssigned())
                            .totalTasksCompleted(feature.getTotalTasksCompleted())
                            // Text analysis features (NEW)
                            .proposalRelevanceScore(feature.getProposalRelevanceScore())
                            .keywordCoverageScore(feature.getKeywordCoverageScore())
                            .combinedTextScore(feature.getCombinedTextScore())
                            // Task context
                            .taskBaseCredits(feature.getTaskBaseCredits())
                            .taskSkillCount(feature.getTaskSkillCount())
                            .taskCategory(feature.getTaskCategory())
                            .heuristicScore(feature.getHeuristicScore())
                            // Add ML prediction
                            .mlPredictedScore(response.getSuccessProbability())
                            .mlConfidence(response.getConfidence())
                            .predictedSuccessProbability(response.getSuccessProbability())
                            .usedMlPrediction(true)
                            .build();
                    results.add(updated);
                }
            } catch (Exception e) {
                log.debug("ML prediction failed for bid {}: {}", feature.getBidId(), e.getMessage());
                throw new RuntimeException("ML service unavailable", e);
            }
        }

        // Sort by ML predicted score (descending)
        return results.stream()
                .sorted(Comparator.comparingDouble(BidFeatureSnapshot::getMlPredictedScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Make HTTP call to ML service
     */
    private MLPredictionResponse callMLService(BidFeatureSnapshot features) {
        MLPredictionRequest request = MLPredictionRequest.fromSnapshot(features);

        return mlWebClient.post()
                .uri("/predict")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLPredictionResponse.class)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("ML service returned error: {} - {}", ex.getStatusCode(), ex.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("ML service call failed: {}", ex.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Rank bids using heuristic score (fallback)
     */
    private List<BidFeatureSnapshot> rankByHeuristic(List<BidFeatureSnapshot> features) {
        log.info("Using heuristic ranking for {} bids", features.size());
        return features.stream()
                .sorted(Comparator.comparingDouble(BidFeatureSnapshot::getHeuristicScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Check if ML service is healthy
     */
    public boolean isMLServiceHealthy() {
        if (!mlConfig.isMlEnabled()) {
            return false;
        }

        try {
            String health = mlWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn("")
                    .block();
            return health != null && !health.isEmpty();
        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the final score to use for ranking/selection
     */
    public double getFinalScore(BidFeatureSnapshot snapshot) {
        if (snapshot.getUsedMlPrediction() != null && snapshot.getUsedMlPrediction() 
                && snapshot.getMlPredictedScore() != null) {
            return snapshot.getMlPredictedScore();
        }
        return snapshot.getHeuristicScore();
    }

    /**
     * Check if ranking requires manual confirmation
     * 
     * Confidence-Aware Automation:
     * If the difference between top 2 bids is less than CONFIDENCE_THRESHOLD,
     * the system cannot confidently auto-select and should require manual confirmation.
     * 
     * @param rankedBids List of bids sorted by score (descending)
     * @return true if manual confirmation is required (top bids too close)
     */
    public boolean requiresManualConfirmation(List<BidFeatureSnapshot> rankedBids) {
        if (rankedBids == null || rankedBids.size() < 2) {
            // Only one bid - no need for manual confirmation
            return false;
        }

        BidFeatureSnapshot top = rankedBids.get(0);
        BidFeatureSnapshot second = rankedBids.get(1);

        double topScore = getFinalScore(top);
        double secondScore = getFinalScore(second);
        double difference = topScore - secondScore;

        boolean requiresManual = difference < CONFIDENCE_THRESHOLD;
        
        if (requiresManual) {
            log.info("Manual confirmation required: top bid ({}) score {} vs second bid ({}) score {} - difference {} < threshold {}",
                    top.getBidId(), topScore, second.getBidId(), secondScore, difference, CONFIDENCE_THRESHOLD);
        }

        return requiresManual;
    }

    /**
     * Get the confidence margin between top 2 bids
     * Useful for displaying to users why manual selection is needed
     */
    public double getConfidenceMargin(List<BidFeatureSnapshot> rankedBids) {
        if (rankedBids == null || rankedBids.size() < 2) {
            return 1.0; // Full confidence with single bid
        }

        double topScore = getFinalScore(rankedBids.get(0));
        double secondScore = getFinalScore(rankedBids.get(1));
        
        return topScore - secondScore;
    }

    /**
     * Get the confidence threshold for auto-assignment
     */
    public double getConfidenceThreshold() {
        return CONFIDENCE_THRESHOLD;
    }
}
