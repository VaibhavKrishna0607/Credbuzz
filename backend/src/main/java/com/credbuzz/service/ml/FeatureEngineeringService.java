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
 * Feature Engineering Service - IMPROVED
 * ============================================
 * 
 * Computes ML features for bid evaluation with proper normalization.
 * All features are scaled between 0 and 1.
 * 
 * IMPROVEMENTS:
 * 1. Cold-start baseline for new users (completionRate=0.6, lateRatio=0.1)
 * 2. Skill match with fallback to proposal text analysis
 * 3. Credit fairness properly normalized
 * 4. Deadline realism score (0-1)
 * 5. All features properly scaled
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
     */
    public BidFeatureSnapshot computeFeatures(Bid bid, Task task) {
        User bidder = bid.getBidder();
        UserPerformance performance = getOrCreatePerformance(bidder.getId());

        // Compute all features (all normalized 0-1)
        int experienceLevel = performance.getTasksCompleted();
        double skillMatchScore = computeSkillMatchScore(task.getSkills(), bidder.getSkills(), bid.getProposalMessage());
        double creditDelta = computeCreditDelta(bid.getProposedCredits(), task.getCredits());
        double creditFairness = computeCreditFairness(creditDelta);
        double deadlineRealism = computeDeadlineRealism(bid.getProposedCompletionDays(), performance);
        double completionRate = computeCompletionRateWithColdStart(performance, experienceLevel);
        double avgRating = normalizeRating(performance.getAvgRating());
        double lateRatio = computeLateRatioWithColdStart(performance, experienceLevel);
        double workloadScore = computeWorkloadScore(performance);
        double onTimeRate = performance.getOnTimeRate();
        double bidWinRate = performance.getBidWinRate();

        // Compute text analysis features
        TextAnalysisService.TextAnalysisResult textResult = textAnalysisService.analyzeProposal(
                task.getDescription(),
                task.getSkills(),
                bid.getProposalMessage()
        );

        // Compute heuristic score
        double heuristicScore = computeHeuristicScore(
                skillMatchScore, creditFairness, deadlineRealism, 
                completionRate, avgRating, lateRatio, workloadScore, experienceLevel,
                textResult.getProposalRelevanceScore(), textResult.getKeywordCoverageScore()
        );

        return BidFeatureSnapshot.builder()
                .bidId(bid.getId())
                .bidderId(bidder.getId())
                .taskId(task.getId())
                .bidderName(bidder.getName())
                // Proposal features (all 0-1)
                .skillMatchScore(skillMatchScore)
                .creditDelta(creditDelta)
                .creditFairness(creditFairness)
                .deadlineDelta(deadlineRealism)
                .deadlineRealism(deadlineRealism)
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                // Bidder features (all 0-1)
                .completionRate(completionRate)
                .avgRating(avgRating)
                .lateRatio(lateRatio)
                .workloadScore(workloadScore)
                .experienceLevel(experienceLevel)
                .onTimeRate(onTimeRate)
                .bidWinRate(bidWinRate)
                .totalTasksAssigned(performance.getTasksAssigned())
                .totalTasksCompleted(performance.getTasksCompleted())
                // Text analysis features (all 0-1)
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
     * Compute features for all bids (batch processing)
     */
    public List<BidFeatureSnapshot> computeFeaturesForAllBids(List<Bid> bids, Task task) {
        if (bids == null || bids.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract proposal texts for batch analysis
        List<String> proposalTexts = bids.stream()
                .map(bid -> bid.getProposalMessage() != null ? bid.getProposalMessage() : "")
                .collect(Collectors.toList());

        // Batch text analysis
        List<TextAnalysisService.TextAnalysisResult> textResults = 
                textAnalysisService.analyzeProposalsBatch(
                        task.getDescription(),
                        task.getSkills(),
                        proposalTexts
                );

        // Compute features for each bid
        List<BidFeatureSnapshot> results = new ArrayList<>();
        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            TextAnalysisService.TextAnalysisResult textResult = 
                    i < textResults.size() ? textResults.get(i) : TextAnalysisService.TextAnalysisResult.defaults();
            
            results.add(computeFeaturesWithTextResult(bid, task, textResult));
        }

        return results;
    }

    private BidFeatureSnapshot computeFeaturesWithTextResult(
            Bid bid, Task task, TextAnalysisService.TextAnalysisResult textResult) {
        
        User bidder = bid.getBidder();
        UserPerformance performance = getOrCreatePerformance(bidder.getId());

        int experienceLevel = performance.getTasksCompleted();
        double skillMatchScore = computeSkillMatchScore(task.getSkills(), bidder.getSkills(), bid.getProposalMessage());
        double creditDelta = computeCreditDelta(bid.getProposedCredits(), task.getCredits());
        double creditFairness = computeCreditFairness(creditDelta);
        double deadlineRealism = computeDeadlineRealism(bid.getProposedCompletionDays(), performance);
        double completionRate = computeCompletionRateWithColdStart(performance, experienceLevel);
        double avgRating = normalizeRating(performance.getAvgRating());
        double lateRatio = computeLateRatioWithColdStart(performance, experienceLevel);
        double workloadScore = computeWorkloadScore(performance);
        double onTimeRate = performance.getOnTimeRate();
        double bidWinRate = performance.getBidWinRate();

        double heuristicScore = computeHeuristicScore(
                skillMatchScore, creditFairness, deadlineRealism, 
                completionRate, avgRating, lateRatio, workloadScore, experienceLevel,
                textResult.getProposalRelevanceScore(), textResult.getKeywordCoverageScore()
        );

        return BidFeatureSnapshot.builder()
                .bidId(bid.getId())
                .bidderId(bidder.getId())
                .taskId(task.getId())
                .bidderName(bidder.getName())
                .skillMatchScore(skillMatchScore)
                .creditDelta(creditDelta)
                .creditFairness(creditFairness)
                .deadlineDelta(deadlineRealism)
                .deadlineRealism(deadlineRealism)
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                .completionRate(completionRate)
                .avgRating(avgRating)
                .lateRatio(lateRatio)
                .workloadScore(workloadScore)
                .experienceLevel(experienceLevel)
                .onTimeRate(onTimeRate)
                .bidWinRate(bidWinRate)
                .totalTasksAssigned(performance.getTasksAssigned())
                .totalTasksCompleted(performance.getTasksCompleted())
                .proposalRelevanceScore(textResult.getProposalRelevanceScore())
                .keywordCoverageScore(textResult.getKeywordCoverageScore())
                .combinedTextScore(textResult.getCombinedTextScore())
                .taskBaseCredits(task.getCredits())
                .taskSkillCount(task.getSkills() != null ? task.getSkills().size() : 0)
                .taskCategory(task.getCategory())
                .heuristicScore(heuristicScore)
                .usedMlPrediction(false)
                .build();
    }

    // ================================
    // FEATURE COMPUTATION METHODS
    // ================================

    /**
     * Compute skill match score with fallback to proposal text
     * 
     * If profile skills exist: compute intersection ratio
     * If profile skills missing: extract from proposal text
     * Returns: 0.0 - 1.0
     */
    public double computeSkillMatchScore(List<String> taskSkills, List<String> bidderSkills, String proposalText) {
        if (taskSkills == null || taskSkills.isEmpty()) {
            return 1.0; // No skills required
        }
        
        // Try profile skills first
        if (bidderSkills != null && !bidderSkills.isEmpty()) {
            return computeSkillMatchFromProfile(taskSkills, bidderSkills);
        }
        
        // Fallback to proposal text
        if (proposalText != null && !proposalText.isEmpty()) {
            return computeSkillMatchFromProposal(taskSkills, proposalText);
        }
        
        return 0.2; // Baseline for no skills
    }

    private double computeSkillMatchFromProfile(List<String> taskSkills, List<String> bidderSkills) {
        Set<String> taskSet = taskSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());
        
        Set<String> bidderSet = bidderSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        long matched = taskSet.stream()
                .filter(bidderSet::contains)
                .count();

        // Intersection ratio: matched_keywords / total_required_keywords, normalized 0-1
        int totalRequired = taskSet.size();
        return totalRequired > 0 ? Math.min(1.0, (double) matched / totalRequired) : 1.0;
    }

    private double computeSkillMatchFromProposal(List<String> taskSkills, String proposalText) {
        if (proposalText == null || proposalText.isEmpty()) return 0.2;
        String proposalLower = proposalText.toLowerCase();
        
        long matched = taskSkills.stream()
                .filter(skill -> proposalLower.contains(skill.toLowerCase().trim()))
                .count();
        
        int totalRequired = taskSkills.size();
        return totalRequired > 0 ? Math.min(1.0, (double) matched / totalRequired) : 1.0;
    }

    /**
     * Compute credit delta and fairness
     * 
     * creditDelta = abs(proposed - base) / base
     * creditFairness = 1 - creditDelta
     * Returns: 0.0 - 1.0
     */
    public double computeCreditDelta(int proposedCredits, int baseCredits) {
        if (baseCredits <= 0) return 0.0;
        double delta = Math.abs(proposedCredits - baseCredits) / (double) baseCredits;
        return Math.min(1.0, delta);
    }

    public double computeCreditFairness(double creditDelta) {
        return Math.max(0.0, Math.min(1.0, 1.0 - creditDelta));
    }

    /**
     * Compute deadline realism score.
     * Compares bid days to expected days (user's average or default 7).
     * Penalizes overly aggressive timelines; normalizes between 0 and 1.
     */
    public double computeDeadlineRealism(int proposedDays, UserPerformance performance) {
        double expectedDays = performance.getAvgCompletionDays();
        if (expectedDays <= 0) expectedDays = 7.0;
        
        double ratio = proposedDays / expectedDays;
        
        // 0 = very aggressive, 1 = realistic
        double score;
        if (ratio < 0.5) {
            // Overly aggressive: strong penalty
            score = ratio * 0.8; // 0 to 0.4
        } else if (ratio <= 1.2) {
            // Realistic: high score
            score = 0.4 + (ratio - 0.5) * 0.86; // 0.4 to ~1.0
        } else if (ratio <= 2.0) {
            // Slightly long: moderate
            score = Math.max(0.5, 1.0 - (ratio - 1.2) * 0.6);
        } else {
            // Too long
            score = Math.max(0.2, 0.5 - (ratio - 2.0) * 0.15);
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Compute completion rate with cold-start baseline.
     * Do NOT treat new users as 0%.
     * 
     * If experienceLevel (tasksCompleted) == 0: return 0.6
     * Else: Bayesian (completed + alpha) / (assigned + beta)
     * Returns: 0.0 - 1.0
     */
    public double computeCompletionRateWithColdStart(UserPerformance performance, int experienceLevel) {
        if (experienceLevel == 0) {
            return 0.6;
        }
        int completed = performance.getTasksCompleted();
        int assigned = performance.getTasksAssigned();
        if (assigned == 0) return 0.6;
        return Math.min(1.0, (completed + BAYESIAN_ALPHA) / (assigned + BAYESIAN_BETA));
    }

    /**
     * Compute late ratio with cold-start baseline.
     * If experienceLevel == 0: return 0.1 (reliability baseline).
     * Returns: 0.0 - 1.0
     */
    public double computeLateRatioWithColdStart(UserPerformance performance, int experienceLevel) {
        if (experienceLevel == 0) {
            return 0.1;
        }
        int totalCompleted = performance.getOnTimeCompletions() + performance.getLateCompletions();
        if (totalCompleted == 0) return 0.1;
        return (double) performance.getLateCompletions() / totalCompleted;
    }

    /** @deprecated Use computeCompletionRateWithColdStart */
    public double computeBayesianCompletionRate(UserPerformance performance) {
        return computeCompletionRateWithColdStart(performance, performance.getTasksCompleted());
    }

    /** @deprecated Use computeLateRatioWithColdStart */
    public double computeLateRatio(UserPerformance performance) {
        return computeLateRatioWithColdStart(performance, performance.getTasksCompleted());
    }

    /**
     * Compute workload score, normalized 0-1 (higher = busier).
     */
    public double computeWorkloadScore(UserPerformance performance) {
        int activeTasks = performance.getActiveTasksCount();
        return Math.min(1.0, (double) activeTasks / WORKLOAD_THRESHOLD);
    }

    /**
     * Normalize rating to 0-1 scale
     * Returns: 0.0 - 1.0
     */
    public double normalizeRating(double avgRating) {
        if (avgRating <= 0) return 0.5; // Neutral
        return Math.min(1.0, avgRating / 5.0);
    }

    /**
     * Compute heuristic score with proper feature weights
     * All inputs are 0-1 normalized
     * Returns: 0.0 - 1.0
     */
    public double computeHeuristicScore(
            double skillMatchScore,
            double creditFairness,
            double deadlineRealism,
            double completionRate,
            double avgRating,
            double lateRatio,
            double workloadScore,
            int experienceLevel,
            double proposalRelevanceScore,
            double keywordCoverageScore
    ) {
        // Weights (sum to 1.0)
        final double W_COMPLETION = 0.18;
        final double W_LATE = 0.12;
        final double W_RATING = 0.10;
        final double W_SKILL = 0.18;
        final double W_RELEVANCE = 0.10;
        final double W_COVERAGE = 0.05;
        final double W_DEADLINE = 0.08;
        final double W_CREDIT = 0.07;
        final double W_WORKLOAD = 0.05;
        final double W_EXPERIENCE = 0.07;

        // Invert late ratio and workload (lower is better)
        double punctuality = 1.0 - lateRatio;
        double availability = Math.max(0, 1.0 - workloadScore);
        
        // Normalize experience (log scale)
        double expScore = Math.min(1.0, Math.log10(experienceLevel + 1) / 2.0);

        return W_COMPLETION * completionRate +
               W_LATE * punctuality +
               W_RATING * avgRating +
               W_SKILL * skillMatchScore +
               W_RELEVANCE * proposalRelevanceScore +
               W_COVERAGE * keywordCoverageScore +
               W_DEADLINE * deadlineRealism +
               W_CREDIT * creditFairness +
               W_WORKLOAD * availability +
               W_EXPERIENCE * expScore;
    }

    // ================================
    // HELPERS
    // ================================

    private UserPerformance getOrCreatePerformance(Long userId) {
        return userPerformanceRepository.findByUserId(userId)
                .orElseGet(this::createDefaultPerformance);
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
