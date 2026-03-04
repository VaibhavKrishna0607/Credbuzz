package com.credbuzz.service;

import com.credbuzz.dto.BidScoreDto;
import com.credbuzz.entity.*;
import com.credbuzz.repository.AuctionHistoryRepository;
import com.credbuzz.repository.BidRepository;
import com.credbuzz.repository.UserPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ============================================
 * Bid Evaluation Service
 * ============================================
 * 
 * Core service for ranking bids using a heuristic scoring system.
 * ML/AI integration has been removed for stability.
 * The system uses weighted heuristic scoring based on:
 * - Skill match, completion rate, credit fairness
 * - Deadline realism, rating, workload, on-time rate, bid win rate
 * 
 * Future: ML model predictions can be reintroduced here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidEvaluationService {

    private final BidRepository bidRepository;
    private final UserPerformanceRepository userPerformanceRepository;
    private final AuctionHistoryRepository auctionHistoryRepository;

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
}
