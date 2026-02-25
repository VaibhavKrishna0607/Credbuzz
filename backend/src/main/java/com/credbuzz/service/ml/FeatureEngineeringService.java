package com.credbuzz.service.ml;

import com.credbuzz.dto.ml.BidFeatureSnapshot;
import com.credbuzz.entity.*;
import com.credbuzz.repository.UserPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ============================================
 * Feature Engineering Service
 * ============================================
 * 
 * Computes ML features for bid evaluation.
 * All features are computed once at auction close and stored as snapshots.
 * 
 * Feature Categories:
 * 1. Proposal Features - About the bid itself
 * 2. Bidder Features - Historical performance of the bidder
 * 3. Text Features (NEW) - Proposal relevance and keyword coverage
 * 
 * This service is the single source of truth for feature computation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureEngineeringService {

    private final UserPerformanceRepository userPerformanceRepository;
    private final TextAnalysisService textAnalysisService;

    // Bayesian prior parameters for completion rate
    private static final double BAYESIAN_ALPHA = 3.0;  // Prior successes
    private static final double BAYESIAN_BETA = 5.0;   // Prior total

    // Workload threshold (max reasonable active tasks)
    private static final int WORKLOAD_THRESHOLD = 5;

    /**
     * Compute feature snapshot for a single bid (with text analysis)
     * This is the main entry point for feature computation
     */
    public BidFeatureSnapshot computeFeatures(Bid bid, Task task) {
        User bidder = bid.getBidder();
        UserPerformance performance = getOrCreatePerformance(bidder.getId());

        // Compute all features
        double skillMatchScore = computeSkillMatchScore(task.getSkills(), bidder.getSkills());
        double creditDelta = computeCreditDelta(bid.getProposedCredits(), task.getCredits());
        double deadlineDelta = computeDeadlineDelta(bid.getProposedCompletionDays(), performance);
        double completionRate = computeBayesianCompletionRate(performance);
        double avgRating = normalizeRating(performance.getAvgRating());
        double lateRatio = computeLateRatio(performance);
        double workloadScore = computeWorkloadScore(performance);
        int experienceLevel = performance.getTasksCompleted();
        double onTimeRate = performance.getOnTimeRate();
        double bidWinRate = performance.getBidWinRate();

        // Compute text analysis features (NEW)
        TextAnalysisService.TextAnalysisResult textResult = textAnalysisService.analyzeProposal(
                task.getDescription(),
                task.getSkills(),
                bid.getProposalMessage()
        );

        // Compute heuristic score (now includes text features)
        double heuristicScore = computeHeuristicScore(
                skillMatchScore, creditDelta, deadlineDelta, 
                completionRate, avgRating, lateRatio, workloadScore, experienceLevel,
                textResult.getProposalRelevanceScore(), textResult.getKeywordCoverageScore()
        );

        return BidFeatureSnapshot.builder()
                // Identifiers
                .bidId(bid.getId())
                .bidderId(bidder.getId())
                .taskId(task.getId())
                .bidderName(bidder.getName())
                // Proposal features
                .skillMatchScore(skillMatchScore)
                .creditDelta(creditDelta)
                .deadlineDelta(deadlineDelta)
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                // Bidder features
                .completionRate(completionRate)
                .avgRating(avgRating)
                .lateRatio(lateRatio)
                .workloadScore(workloadScore)
                .experienceLevel(experienceLevel)
                .onTimeRate(onTimeRate)
                .bidWinRate(bidWinRate)
                .totalTasksAssigned(performance.getTasksAssigned())
                .totalTasksCompleted(performance.getTasksCompleted())
                // Text analysis features (NEW)
                .proposalRelevanceScore(textResult.getProposalRelevanceScore())
                .keywordCoverageScore(textResult.getKeywordCoverageScore())
                .combinedTextScore(textResult.getCombinedTextScore())
                // Task context
                .taskBaseCredits(task.getCredits())
                .taskSkillCount(task.getSkills() != null ? task.getSkills().size() : 0)
                .taskCategory(task.getCategory())
                // Scores
                .heuristicScore(heuristicScore)
                .usedMlPrediction(false)
                .build();
    }

    /**
     * Compute features for all bids in a task (uses batch text analysis for efficiency)
     */
    public List<BidFeatureSnapshot> computeFeaturesForAllBids(List<Bid> bids, Task task) {
        if (bids == null || bids.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract all proposal texts for batch analysis
        List<String> proposalTexts = bids.stream()
                .map(bid -> bid.getProposalMessage() != null ? bid.getProposalMessage() : "")
                .collect(Collectors.toList());

        // Batch text analysis (more efficient than individual calls)
        List<TextAnalysisService.TextAnalysisResult> textResults = 
                textAnalysisService.analyzeProposalsBatch(
                        task.getDescription(),
                        task.getSkills(),
                        proposalTexts
                );

        // Compute features for each bid with corresponding text result
        List<BidFeatureSnapshot> results = new ArrayList<>();
        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            TextAnalysisService.TextAnalysisResult textResult = 
                    i < textResults.size() ? textResults.get(i) : TextAnalysisService.TextAnalysisResult.defaults();
            
            results.add(computeFeaturesWithTextResult(bid, task, textResult));
        }

        return results;
    }

    /**
     * Compute features for a bid with pre-computed text analysis result
     */
    private BidFeatureSnapshot computeFeaturesWithTextResult(
            Bid bid, Task task, TextAnalysisService.TextAnalysisResult textResult) {
        
        User bidder = bid.getBidder();
        UserPerformance performance = getOrCreatePerformance(bidder.getId());

        // Compute all features
        double skillMatchScore = computeSkillMatchScore(task.getSkills(), bidder.getSkills());
        double creditDelta = computeCreditDelta(bid.getProposedCredits(), task.getCredits());
        double deadlineDelta = computeDeadlineDelta(bid.getProposedCompletionDays(), performance);
        double completionRate = computeBayesianCompletionRate(performance);
        double avgRating = normalizeRating(performance.getAvgRating());
        double lateRatio = computeLateRatio(performance);
        double workloadScore = computeWorkloadScore(performance);
        int experienceLevel = performance.getTasksCompleted();
        double onTimeRate = performance.getOnTimeRate();
        double bidWinRate = performance.getBidWinRate();

        // Compute heuristic score (now includes text features)
        double heuristicScore = computeHeuristicScore(
                skillMatchScore, creditDelta, deadlineDelta, 
                completionRate, avgRating, lateRatio, workloadScore, experienceLevel,
                textResult.getProposalRelevanceScore(), textResult.getKeywordCoverageScore()
        );

        return BidFeatureSnapshot.builder()
                // Identifiers
                .bidId(bid.getId())
                .bidderId(bidder.getId())
                .taskId(task.getId())
                .bidderName(bidder.getName())
                // Proposal features
                .skillMatchScore(skillMatchScore)
                .creditDelta(creditDelta)
                .deadlineDelta(deadlineDelta)
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                // Bidder features
                .completionRate(completionRate)
                .avgRating(avgRating)
                .lateRatio(lateRatio)
                .workloadScore(workloadScore)
                .experienceLevel(experienceLevel)
                .onTimeRate(onTimeRate)
                .bidWinRate(bidWinRate)
                .totalTasksAssigned(performance.getTasksAssigned())
                .totalTasksCompleted(performance.getTasksCompleted())
                // Text analysis features
                .proposalRelevanceScore(textResult.getProposalRelevanceScore())
                .keywordCoverageScore(textResult.getKeywordCoverageScore())
                .combinedTextScore(textResult.getCombinedTextScore())
                // Task context
                .taskBaseCredits(task.getCredits())
                .taskSkillCount(task.getSkills() != null ? task.getSkills().size() : 0)
                .taskCategory(task.getCategory())
                // Scores
                .heuristicScore(heuristicScore)
                .usedMlPrediction(false)
                .build();
    }

    // ================================
    // PROPOSAL FEATURES
    // ================================

    /**
     * Compute cosine similarity between task skills and bidder skills
     * 
     * Cosine similarity = (A · B) / (||A|| × ||B||)
     * 
     * For discrete skill sets, this becomes:
     * |intersection| / sqrt(|A| × |B|)
     */
    public double computeSkillMatchScore(List<String> taskSkills, List<String> bidderSkills) {
        if (taskSkills == null || taskSkills.isEmpty()) {
            return 1.0; // No skills required = perfect match
        }
        if (bidderSkills == null || bidderSkills.isEmpty()) {
            return 0.0; // Bidder has no skills
        }

        // Normalize to lowercase for comparison
        Set<String> taskSkillSet = taskSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());
        
        Set<String> bidderSkillSet = bidderSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        // Count intersection
        long intersectionSize = taskSkillSet.stream()
                .filter(bidderSkillSet::contains)
                .count();

        // Cosine similarity for binary vectors
        double denominator = Math.sqrt(taskSkillSet.size() * bidderSkillSet.size());
        if (denominator == 0) {
            return 0.0;
        }

        return intersectionSize / denominator;
    }

    /**
     * Compute credit delta (raw difference)
     * Negative = bidder asking for less (good for poster)
     */
    public double computeCreditDelta(int proposedCredits, int baseCredits) {
        return proposedCredits - baseCredits;
    }

    /**
     * Compute deadline delta
     * Negative = bidder proposing faster than their historical average
     */
    public double computeDeadlineDelta(int proposedDays, UserPerformance performance) {
        double avgDays = performance.getAvgCompletionDays();
        if (avgDays <= 0) {
            avgDays = 7.0; // Default for new users
        }
        return proposedDays - avgDays;
    }

    // ================================
    // BIDDER FEATURES
    // ================================

    /**
     * Compute Bayesian-adjusted completion rate
     * 
     * Formula: (completed + alpha) / (total + beta)
     * 
     * This prevents:
     * - New users with 0/0 from having undefined rate
     * - Users with 1/1 from having 100% rate
     * - Uses prior of ~60% (3/5) as baseline
     */
    public double computeBayesianCompletionRate(UserPerformance performance) {
        int completed = performance.getTasksCompleted();
        int total = performance.getTasksAssigned();
        
        return (completed + BAYESIAN_ALPHA) / (total + BAYESIAN_BETA);
    }

    /**
     * Compute late task ratio
     */
    public double computeLateRatio(UserPerformance performance) {
        int totalCompleted = performance.getOnTimeCompletions() + performance.getLateCompletions();
        if (totalCompleted == 0) {
            return 0.0; // No history = assume on time
        }
        return (double) performance.getLateCompletions() / totalCompleted;
    }

    /**
     * Compute workload score (0.0 - 1.0+)
     * Higher = busier (worse for new tasks)
     */
    public double computeWorkloadScore(UserPerformance performance) {
        int activeTasks = performance.getActiveTasksCount();
        return (double) activeTasks / WORKLOAD_THRESHOLD;
    }

    /**
     * Normalize rating to 0.0 - 1.0 scale
     */
    public double normalizeRating(double avgRating) {
        if (avgRating <= 0) {
            return 0.5; // Neutral for no ratings
        }
        return Math.min(1.0, avgRating / 5.0);
    }

    // ================================
    // HEURISTIC SCORING
    // ================================

    /**
     * Compute heuristic score as fallback when ML is unavailable
     * 
     * REBALANCED WEIGHTS (per requirements):
     * Priority order:
     * 1. Historical reliability (completionRate, lateRatio, avgRating) - HIGHEST (40%)
     * 2. Skill match - HIGH (18%)
     * 3. Proposal relevance (NEW) - MEDIUM-HIGH (15%)
     * 4. Timeline realism - MEDIUM (8%)
     * 5. Credit delta - MEDIUM-LOW (7%)
     * 6. Workload risk - LOW (5%)
     * 7. Experience - LOW (7%)
     */
    public double computeHeuristicScore(
            double skillMatchScore,
            double creditDelta,
            double deadlineDelta,
            double completionRate,
            double avgRating,
            double lateRatio,
            double workloadScore,
            int experienceLevel,
            double proposalRelevanceScore,
            double keywordCoverageScore
    ) {
        // REBALANCED WEIGHTS prioritizing historical reliability
        // Historical reliability - HIGHEST priority (40% total)
        final double W_COMPLETION = 0.18;
        final double W_LATE = 0.12;
        final double W_RATING = 0.10;
        
        // Skill match - HIGH priority (18%)
        final double W_SKILL = 0.18;
        
        // Proposal relevance - NEW MEDIUM-HIGH priority (15%)
        final double W_RELEVANCE = 0.10;
        final double W_COVERAGE = 0.05;
        
        // Timeline realism - MEDIUM priority (8%)
        final double W_DEADLINE = 0.08;
        
        // Credit delta - MEDIUM-LOW priority (7%)
        final double W_CREDIT = 0.07;
        
        // Workload risk - LOW priority (5%)
        final double W_WORKLOAD = 0.05;
        
        // Experience - LOW priority (7%)
        final double W_EXPERIENCE = 0.07;

        // Normalize credit delta (negative is good, normalize to 0-1)
        double creditScore = Math.max(0, Math.min(1, 1 - (creditDelta / 100.0)));
        
        // Normalize deadline delta (negative is good)
        double deadlineScore = Math.max(0, Math.min(1, 1 - (deadlineDelta / 14.0)));
        
        // Invert workload (low workload = high score)
        double availabilityScore = Math.max(0, 1 - workloadScore);
        
        // Invert late ratio (low late = high score)
        double punctualityScore = 1 - lateRatio;
        
        // Normalize experience (log scale)
        double experienceScore = Math.min(1, Math.log10(experienceLevel + 1) / 2.0);

        return W_COMPLETION * completionRate +
               W_LATE * punctualityScore +
               W_RATING * avgRating +
               W_SKILL * skillMatchScore +
               W_RELEVANCE * proposalRelevanceScore +
               W_COVERAGE * keywordCoverageScore +
               W_DEADLINE * deadlineScore +
               W_CREDIT * creditScore +
               W_WORKLOAD * availabilityScore +
               W_EXPERIENCE * experienceScore;
    }

    // ================================
    // HELPERS
    // ================================

    private UserPerformance getOrCreatePerformance(Long userId) {
        return userPerformanceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPerformance());
    }

    private UserPerformance createDefaultPerformance() {
        return UserPerformance.builder()
                .tasksCompleted(0)
                .tasksAssigned(0)
                .tasksAbandoned(0)
                .tasksRejected(0)
                .onTimeCompletions(0)
                .lateCompletions(0)
                .avgCompletionDays(7.0)
                .avgRating(0.0)
                .totalRatings(0)
                .activeTasksCount(0)
                .totalBidsPlaced(0)
                .bidsWon(0)
                .avgBidCredits(0.0)
                .skillVerificationScore(0)
                .build();
    }
}
