package com.credbuzz.service;

import com.credbuzz.dto.BidScoreDto;
import com.credbuzz.dto.ml.AuctionResult;
import com.credbuzz.dto.ml.BidFeatureSnapshot;
import com.credbuzz.entity.*;
import com.credbuzz.repository.AuctionHistoryRepository;
import com.credbuzz.repository.BidRepository;
import com.credbuzz.repository.UserPerformanceRepository;
import com.credbuzz.service.ml.FeatureEngineeringService;
import com.credbuzz.service.ml.MLIntegrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ============================================
 * Bid Evaluation Service
 * ============================================
 * 
 * Core service for ranking bids using a heuristic scoring system.
 * This will later be replaced/augmented by ML model predictions.
 * 
 * Current heuristic weights:
 * - 0.4 * skillMatchScore
 * - 0.3 * historicalCompletionRate
 * - 0.2 * creditFairnessScore
 * - 0.1 * deadlineRealismScore
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidEvaluationService {

    private final BidRepository bidRepository;
    private final UserPerformanceRepository userPerformanceRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final MLIntegrationService mlIntegrationService;
    private final ObjectMapper objectMapper;

    // Heuristic weights (can be tuned or replaced by ML model)
    private static final double WEIGHT_SKILL_MATCH = 0.25;
    private static final double WEIGHT_COMPLETION_RATE = 0.25;
    private static final double WEIGHT_CREDIT_FAIRNESS = 0.15;
    private static final double WEIGHT_DEADLINE_REALISM = 0.10;
    private static final double WEIGHT_RATING = 0.10;
    private static final double WEIGHT_WORKLOAD = 0.05;
    private static final double WEIGHT_ON_TIME = 0.05;
    private static final double WEIGHT_BID_WIN_RATE = 0.05;

    /**
     * Evaluate and rank all bids for a task
     * Returns bids sorted by score (highest first)
     */
    public List<BidScoreDto> evaluateAndRankBids(Task task) {
        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        
        if (bids.isEmpty()) {
            return Collections.emptyList();
        }

        // Get performance data for all bidders
        List<Long> bidderIds = bids.stream()
                .map(b -> b.getBidder().getId())
                .collect(Collectors.toList());
        
        Map<Long, UserPerformance> performanceMap = userPerformanceRepository
                .findByUserIdIn(bidderIds)
                .stream()
                .collect(Collectors.toMap(UserPerformance::getUserId, p -> p));

        // Calculate scores for each bid
        List<BidScoreDto> scoredBids = bids.stream()
                .map(bid -> calculateBidScore(bid, task, performanceMap.get(bid.getBidder().getId())))
                .sorted(Comparator.comparingDouble(BidScoreDto::getTotalScore).reversed())
                .collect(Collectors.toList());

        // Assign ranks
        for (int i = 0; i < scoredBids.size(); i++) {
            scoredBids.get(i).setRank(i + 1);
        }

        return scoredBids;
    }

    /**
     * Get the best bid for a task
     */
    public Optional<BidScoreDto> getBestBid(Task task) {
        List<BidScoreDto> rankedBids = evaluateAndRankBids(task);
        return rankedBids.isEmpty() ? Optional.empty() : Optional.of(rankedBids.get(0));
    }

    /**
     * Evaluate and rank bids using ML predictions (simplified version)
     * Fetches bids from repository and returns BidScoreDto list
     */
    public List<BidScoreDto> evaluateWithML(Task task) {
        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        if (bids.isEmpty()) {
            return Collections.emptyList();
        }
        
        AuctionResult result = evaluateWithML(task, bids);
        return convertToScoreDtos(result.getRankedBids());
    }

    /**
     * Evaluate and rank bids using ML predictions - returns full AuctionResult
     * Use this to get confidence-aware automation information
     */
    public AuctionResult evaluateWithMLFullResult(Task task) {
        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        if (bids.isEmpty()) {
            return AuctionResult.builder()
                    .taskId(task.getId())
                    .rankedBids(Collections.emptyList())
                    .requiresManualConfirmation(false)
                    .totalBids(0)
                    .build();
        }
        return evaluateWithML(task, bids);
    }
    
    /**
     * Convert BidFeatureSnapshots to BidScoreDtos
     */
    private List<BidScoreDto> convertToScoreDtos(List<BidFeatureSnapshot> features) {
        if (features == null) return Collections.emptyList();
        
        List<BidScoreDto> result = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            BidFeatureSnapshot f = features.get(i);
            Double creditDelta = f.getCreditDelta() != null ? f.getCreditDelta() : 0.0;
            Double deadlineDelta = f.getDeadlineDelta() != null ? f.getDeadlineDelta() : 0.0;
            
            BidScoreDto dto = BidScoreDto.builder()
                    .bidId(f.getBidId())
                    .bidderId(f.getBidderId())
                    .bidderName(f.getBidderName())
                    .proposedCredits(f.getProposedCredits())
                    .proposedCompletionDays(f.getProposedCompletionDays())
                    .skillMatchScore(f.getSkillMatchScore())
                    .completionRate(f.getCompletionRate())
                    .creditFairnessScore(1.0 - Math.abs(creditDelta))
                    .deadlineRealism(1.0 - Math.abs(deadlineDelta) / 10.0)
                    .ratingScore(f.getAvgRating() != null ? f.getAvgRating() / 5.0 : 0.5)
                    .workloadScore(f.getWorkloadScore())
                    .onTimeScore(f.getOnTimeRate())
                    .bidWinRate(f.getBidWinRate())
                    .totalScore(f.getMlPredictedScore() != null ? f.getMlPredictedScore() : f.getHeuristicScore())
                    .rank(i + 1)
                    .usedMlPrediction(f.getUsedMlPrediction())
                    .mlConfidence(f.getMlConfidence())
                    .build();
            result.add(dto);
        }
        return result;
    }

    /**
     * Evaluate and rank bids using ML predictions (with heuristic fallback)
     * This is the preferred method for production auction closing.
     * 
     * Includes Confidence-Aware Automation:
     * If top 2 bids are too close, sets requiresManualConfirmation=true.
     * 
     * @param task The task whose auction is closing
     * @param bids All bids for the task
     * @return AuctionResult with ranked bids and winner
     */
    public AuctionResult evaluateWithML(Task task, List<Bid> bids) {
        if (bids == null || bids.isEmpty()) {
            return AuctionResult.builder()
                    .taskId(task.getId())
                    .rankedBids(Collections.emptyList())
                    .requiresManualConfirmation(false)
                    .build();
        }

        // Compute features for all bids
        List<BidFeatureSnapshot> features = featureEngineeringService.computeFeaturesForAllBids(bids, task);

        // Get ML predictions (with automatic fallback to heuristic)
        List<BidFeatureSnapshot> rankedBids = mlIntegrationService.predictAndRankBids(features);

        // Check if manual confirmation is required (confidence-aware automation)
        boolean requiresManualConfirmation = mlIntegrationService.requiresManualConfirmation(rankedBids);
        double confidenceMargin = mlIntegrationService.getConfidenceMargin(rankedBids);

        // Build auction result
        BidFeatureSnapshot winner = rankedBids.isEmpty() ? null : rankedBids.get(0);
        
        return AuctionResult.builder()
                .taskId(task.getId())
                .rankedBids(rankedBids)
                .winningBidId(winner != null ? winner.getBidId() : null)
                .winningScore(winner != null ? mlIntegrationService.getFinalScore(winner) : null)
                .usedMlPrediction(winner != null && Boolean.TRUE.equals(winner.getUsedMlPrediction()))
                // Confidence-aware automation fields
                .requiresManualConfirmation(requiresManualConfirmation)
                .confidenceMargin(confidenceMargin)
                .confidenceThreshold(mlIntegrationService.getConfidenceThreshold())
                .totalBids(bids.size())
                .build();
    }

    /**
     * Calculate score for a single bid
     */
    private BidScoreDto calculateBidScore(Bid bid, Task task, UserPerformance performance) {
        User bidder = bid.getBidder();
        
        // Use default performance if none exists
        if (performance == null) {
            performance = createDefaultPerformance(bidder);
        }

        // Calculate individual scores
        double skillMatchScore = calculateSkillMatchScore(task, bidder);
        double completionRate = performance.getCompletionRate();
        double creditFairnessScore = calculateCreditFairnessScore(bid, task);
        double deadlineRealismScore = calculateDeadlineRealismScore(bid, task);
        double ratingScore = normalizeRating(performance.getAvgRating());
        double workloadScore = calculateWorkloadScore(performance);
        double onTimeScore = performance.getOnTimeRate();
        double bidWinRateScore = performance.getBidWinRate();

        // Calculate weighted total score
        double totalScore = 
                WEIGHT_SKILL_MATCH * skillMatchScore +
                WEIGHT_COMPLETION_RATE * completionRate +
                WEIGHT_CREDIT_FAIRNESS * creditFairnessScore +
                WEIGHT_DEADLINE_REALISM * deadlineRealismScore +
                WEIGHT_RATING * ratingScore +
                WEIGHT_WORKLOAD * workloadScore +
                WEIGHT_ON_TIME * onTimeScore +
                WEIGHT_BID_WIN_RATE * bidWinRateScore;

        return BidScoreDto.builder()
                .bidId(bid.getId())
                .bidderId(bidder.getId())
                .bidderName(bidder.getName())
                .skillMatchScore(skillMatchScore)
                .completionRate(completionRate)
                .creditFairnessScore(creditFairnessScore)
                .deadlineRealism(deadlineRealismScore)
                .ratingScore(ratingScore)
                .workloadScore(workloadScore)
                .onTimeScore(onTimeScore)
                .bidWinRate(bidWinRateScore)
                .totalScore(totalScore)
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                .proposalMessage(bid.getProposalMessage())
                .build();
    }

    /**
     * Calculate skill match score (0.0 - 1.0)
     * Measures how well bidder's skills match task requirements
     */
    private double calculateSkillMatchScore(Task task, User bidder) {
        List<String> taskSkills = task.getSkills();
        List<String> bidderSkills = bidder.getSkills();

        if (taskSkills == null || taskSkills.isEmpty()) {
            return 1.0; // No skills required, everyone matches
        }
        if (bidderSkills == null || bidderSkills.isEmpty()) {
            return 0.0; // Bidder has no skills
        }

        // Case-insensitive skill matching
        Set<String> bidderSkillsLower = bidderSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long matchCount = taskSkills.stream()
                .map(String::toLowerCase)
                .filter(bidderSkillsLower::contains)
                .count();

        return (double) matchCount / taskSkills.size();
    }

    /**
     * Calculate credit fairness score (0.0 - 1.0)
     * Lower proposed credits relative to task credits = higher score
     */
    private double calculateCreditFairnessScore(Bid bid, Task task) {
        if (task.getCredits() == null || task.getCredits() == 0) {
            return 0.5;
        }

        double ratio = (double) bid.getProposedCredits() / task.getCredits();
        
        // If bidder asks for less, give higher score
        // Score = 1 - (proposed/original), capped between 0 and 1
        // Asking for 100% of credits = 0 score
        // Asking for 50% of credits = 0.5 score
        // Asking for 20% of credits = 0.8 score
        return Math.max(0, Math.min(1, 1 - ratio));
    }

    /**
     * Calculate deadline realism score (0.0 - 1.0)
     * Reasonable timelines score higher than too short or too long
     */
    private double calculateDeadlineRealismScore(Bid bid, Task task) {
        int proposedDays = bid.getProposedCompletionDays();
        
        // Get historical average for this category
        Double avgDays = null;
        if (task.getCategory() != null) {
            avgDays = auctionHistoryRepository.getAvgCompletionDaysByCategory(task.getCategory());
        }
        
        // Default typical completion time if no history
        double typicalDays = avgDays != null ? avgDays : 7.0;

        // Score based on how close to typical
        // Exactly typical = 1.0
        // 2x typical = 0.5
        // 0.5x typical might be unrealistic = 0.7
        double ratio = proposedDays / typicalDays;
        
        if (ratio >= 0.5 && ratio <= 1.5) {
            // Reasonable range - high score
            return 1.0 - Math.abs(1 - ratio) * 0.3;
        } else if (ratio < 0.5) {
            // Too fast - might be unrealistic
            return 0.5 * ratio;
        } else {
            // Too slow - less desirable
            return Math.max(0, 1 - (ratio - 1.5) * 0.2);
        }
    }

    /**
     * Normalize rating to 0-1 scale
     */
    private double normalizeRating(Double avgRating) {
        if (avgRating == null || avgRating == 0) {
            return 0.5; // New users get neutral score
        }
        return avgRating / 5.0;
    }

    /**
     * Calculate workload score (0.0 - 1.0)
     * Lower active tasks = higher score (more available)
     */
    private double calculateWorkloadScore(UserPerformance performance) {
        int activeTasks = performance.getActiveTasksCount();
        
        // Score decreases as active tasks increase
        // 0 tasks = 1.0
        // 3 tasks = 0.5
        // 6+ tasks = ~0.1
        return Math.max(0.1, 1.0 - (activeTasks * 0.15));
    }

    /**
     * Create default performance for new users
     */
    private UserPerformance createDefaultPerformance(User user) {
        return UserPerformance.builder()
                .user(user)
                .tasksCompleted(0)
                .tasksAssigned(0)
                .avgRating(0.0)
                .activeTasksCount(0)
                .onTimeCompletions(0)
                .lateCompletions(0)
                .totalBidsPlaced(0)
                .bidsWon(0)
                .build();
    }

    /**
     * Record auction history for ML training
     */
    @Transactional
    public void recordAuctionHistory(Task task, List<Bid> bids, Bid selectedBid) {
        // Compute features for all bids
        List<BidFeatureSnapshot> features = featureEngineeringService.computeFeaturesForAllBids(bids, task);
        Map<Long, BidFeatureSnapshot> featureMap = features.stream()
                .collect(Collectors.toMap(BidFeatureSnapshot::getBidId, f -> f));

        for (Bid bid : bids) {
            BidFeatureSnapshot snapshot = featureMap.get(bid.getId());
            if (snapshot == null) {
                log.warn("No feature snapshot for bid {}, skipping", bid.getId());
                continue;
            }

            // Serialize feature snapshot to JSON
            String snapshotJson = null;
            try {
                snapshotJson = objectMapper.writeValueAsString(snapshot);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize feature snapshot for bid {}: {}", bid.getId(), e.getMessage());
            }

            AuctionHistory history = AuctionHistory.builder()
                    .taskId(task.getId())
                    .bidId(bid.getId())
                    .bidderId(bid.getBidder().getId())
                    .posterId(task.getPoster().getId())
                    .taskTitle(task.getTitle())
                    .taskCategory(task.getCategory())
                    .originalCredits(task.getCredits())
                    .taskSkillCount(task.getSkills() != null ? task.getSkills().size() : 0)
                    .proposedCredits(bid.getProposedCredits())
                    .proposedCompletionDays(bid.getProposedCompletionDays())
                    // Feature columns (for quick queries)
                    .skillMatchScore(snapshot.getSkillMatchScore())
                    .completionRate(snapshot.getCompletionRate())
                    .creditDelta(snapshot.getCreditDelta())
                    .deadlineDelta(snapshot.getDeadlineDelta())
                    .avgRating(snapshot.getAvgRating())
                    .workloadScore(snapshot.getWorkloadScore())
                    .onTimeRate(snapshot.getOnTimeRate())
                    .bidWinRate(snapshot.getBidWinRate())
                    .heuristicScore(snapshot.getHeuristicScore())
                    // JSON snapshot for ML training
                    .featureSnapshotJson(snapshotJson)
                    .mlPredictedScore(snapshot.getMlPredictedScore())
                    .usedMlPrediction(Boolean.TRUE.equals(snapshot.getUsedMlPrediction()))
                    // Outcome
                    .wasSelected(bid.getId().equals(selectedBid.getId()))
                    .completedSuccessfully(false) // Will be updated when task completes
                    .build();

            auctionHistoryRepository.save(history);
        }

        log.info("Recorded {} auction history records for task {}", bids.size(), task.getId());
    }

    /**
     * Record auction history with ML feature snapshots
     */
    @Transactional
    public void recordAuctionHistoryWithML(Task task, List<Bid> bids, Bid selectedBid, 
                                           List<BidFeatureSnapshot> rankedFeatures) {
        Map<Long, BidFeatureSnapshot> featureMap = rankedFeatures.stream()
                .collect(Collectors.toMap(BidFeatureSnapshot::getBidId, f -> f));

        for (Bid bid : bids) {
            BidFeatureSnapshot snapshot = featureMap.get(bid.getId());
            if (snapshot == null) {
                // Compute features if not in ranked list
                snapshot = featureEngineeringService.computeFeatures(bid, task);
            }

            // Serialize feature snapshot to JSON
            String snapshotJson = null;
            try {
                snapshotJson = objectMapper.writeValueAsString(snapshot);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize feature snapshot for bid {}: {}", bid.getId(), e.getMessage());
            }

            AuctionHistory history = AuctionHistory.builder()
                    .taskId(task.getId())
                    .bidId(bid.getId())
                    .bidderId(bid.getBidder().getId())
                    .posterId(task.getPoster().getId())
                    .taskTitle(task.getTitle())
                    .taskCategory(task.getCategory())
                    .originalCredits(task.getCredits())
                    .taskSkillCount(task.getSkills() != null ? task.getSkills().size() : 0)
                    .proposedCredits(bid.getProposedCredits())
                    .proposedCompletionDays(bid.getProposedCompletionDays())
                    .skillMatchScore(snapshot.getSkillMatchScore())
                    .completionRate(snapshot.getCompletionRate())
                    .creditDelta(snapshot.getCreditDelta())
                    .deadlineDelta(snapshot.getDeadlineDelta())
                    .avgRating(snapshot.getAvgRating())
                    .workloadScore(snapshot.getWorkloadScore())
                    .onTimeRate(snapshot.getOnTimeRate())
                    .bidWinRate(snapshot.getBidWinRate())
                    .heuristicScore(snapshot.getHeuristicScore())
                    .featureSnapshotJson(snapshotJson)
                    .mlPredictedScore(snapshot.getMlPredictedScore())
                    .usedMlPrediction(Boolean.TRUE.equals(snapshot.getUsedMlPrediction()))
                    .wasSelected(bid.getId().equals(selectedBid.getId()))
                    .completedSuccessfully(false)
                    .build();

            auctionHistoryRepository.save(history);
        }

        log.info("Recorded {} ML auction history records for task {}", bids.size(), task.getId());
    }

    private double calculateCreditDelta(Bid bid, Task task) {
        if (task.getCredits() == null || task.getCredits() == 0) return 0;
        return (double) (bid.getProposedCredits() - task.getCredits()) / task.getCredits();
    }

    private double calculateDeadlineDelta(Bid bid, Task task) {
        // For now, use a default typical days; can be enhanced with category averages
        double typicalDays = 7.0;
        if (task.getCategory() != null) {
            Double avg = auctionHistoryRepository.getAvgCompletionDaysByCategory(task.getCategory());
            if (avg != null) typicalDays = avg;
        }
        return (bid.getProposedCompletionDays() - typicalDays) / typicalDays;
    }

    /**
     * Update auction history when task is completed
     */
    @Transactional
    public void updateAuctionOutcome(Task task, boolean completedSuccessfully, 
                                      boolean wasOnTime, int actualDays, Double rating) {
        List<AuctionHistory> histories = auctionHistoryRepository.findByTaskId(task.getId());
        
        for (AuctionHistory history : histories) {
            if (history.getWasSelected()) {
                history.setCompletedSuccessfully(completedSuccessfully);
                history.setWasOnTime(wasOnTime);
                history.setActualCompletionDays(actualDays);
                history.setPosterRating(rating);
                history.setTaskCompletedAt(task.getCompletedAt());
                auctionHistoryRepository.save(history);
            }
        }
    }
}
