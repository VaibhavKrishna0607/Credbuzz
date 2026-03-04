package com.credbuzz.service;

import com.credbuzz.dto.ReputationScore;
import com.credbuzz.dto.ReputationScore.ReputationTier;
import com.credbuzz.entity.User;
import com.credbuzz.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reputation Engine - Calculates comprehensive reputation scores.
 * 
 * Formula: FinalScore = w_ai * AI_ComplianceScore + w_creator * CreatorScore + w_reliability * ReliabilityScore
 * 
 * Default weights:
 * - AI Compliance: 40% (objective measure)
 * - Creator Satisfaction: 30% (subjective measure)  
 * - Reliability: 30% (behavioral measure)
 * 
 * Abuse Protection:
 * - If average divergence between AI and Creator scores > threshold:
 *   - Reduce creator weight, redistribute to AI weight
 *   - This protects bidders from malicious low ratings
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReputationService {

    private final SubmissionRepository submissionRepository;
    private final TaskRatingRepository taskRatingRepository;
    private final TaskRepository taskRepository;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;

    // Default weights
    private static final double DEFAULT_AI_WEIGHT = 0.40;
    private static final double DEFAULT_CREATOR_WEIGHT = 0.30;
    private static final double DEFAULT_RELIABILITY_WEIGHT = 0.30;
    
    // Divergence threshold for weight adjustment
    private static final double DIVERGENCE_THRESHOLD = 25.0;
    private static final double REDUCED_CREATOR_WEIGHT = 0.15;
    
    // Minimum tasks for meaningful score
    private static final int MIN_TASKS_FOR_SCORE = 1;

    /**
     * Calculate comprehensive reputation score for a user.
     */
    public ReputationScore calculateReputation(User user) {
        ReputationScore.ReputationScoreBuilder builder = ReputationScore.builder()
                .userId(user.getId())
                .userName(user.getName());

        // Get component scores
        Double aiScore = calculateAIComplianceScore(user);
        Double creatorScore = calculateCreatorSatisfactionScore(user);
        Double reliabilityScore = calculateReliabilityScore(user);
        
        // Get statistics
        int completedTasks = getCompletedTaskCount(user);
        int totalRatings = (int) taskRatingRepository.countByRatedUser(user);
        
        builder.aiComplianceScore(aiScore)
               .creatorSatisfactionScore(creatorScore)
               .reliabilityScore(reliabilityScore)
               .completedTasks(completedTasks)
               .totalRatings(totalRatings);

        // Calculate divergence and adjust weights
        Double averageDivergence = calculateAverageDivergence(user);
        builder.averageDivergence(averageDivergence);

        double aiWeight = DEFAULT_AI_WEIGHT;
        double creatorWeight = DEFAULT_CREATOR_WEIGHT;
        double reliabilityWeight = DEFAULT_RELIABILITY_WEIGHT;
        boolean creatorWeightReduced = false;

        // Abuse protection: reduce creator weight if high divergence
        if (averageDivergence != null && averageDivergence > DIVERGENCE_THRESHOLD) {
            creatorWeight = REDUCED_CREATOR_WEIGHT;
            aiWeight = DEFAULT_AI_WEIGHT + (DEFAULT_CREATOR_WEIGHT - REDUCED_CREATOR_WEIGHT);
            creatorWeightReduced = true;
            log.warn("High divergence ({:.2f}) detected for user {}. Creator weight reduced.", 
                    averageDivergence, user.getName());
        }

        builder.aiWeight(aiWeight)
               .creatorWeight(creatorWeight)
               .reliabilityWeight(reliabilityWeight)
               .creatorWeightReduced(creatorWeightReduced);

        // Calculate final score
        double finalScore = calculateFinalScore(
                aiScore, creatorScore, reliabilityScore,
                aiWeight, creatorWeight, reliabilityWeight);
        
        builder.finalScore(finalScore);

        // Determine tier
        ReputationTier tier = ReputationTier.fromScore(finalScore, completedTasks);
        builder.tier(tier);

        // Add rates
        builder.completionRate(calculateCompletionRate(user))
               .onTimeRate(calculateOnTimeRate(user))
               .disputeLossRate(calculateDisputeLossRate(user));

        ReputationScore score = builder.build();
        
        log.debug("Calculated reputation for {}: AI={:.2f}, Creator={:.2f}, Reliability={:.2f}, Final={:.2f}, Tier={}", 
                user.getName(), aiScore, creatorScore, reliabilityScore, finalScore, tier);

        return score;
    }

    /**
     * Calculate AI Compliance Score (average of all AI review scores)
     */
    private Double calculateAIComplianceScore(User user) {
        Double avgScore = submissionRepository.getAverageAIScoreForUser(user);
        return avgScore != null ? avgScore : 70.0; // Default for new users
    }

    /**
     * Calculate Creator Satisfaction Score (from task ratings)
     */
    private Double calculateCreatorSatisfactionScore(User user) {
        Double avgRating = taskRatingRepository.getOverallAverageRating(user);
        if (avgRating == null) {
            return 70.0; // Default for new users
        }
        // Convert 1-5 scale to 0-100
        return avgRating * 20.0;
    }

    /**
     * Calculate Reliability Score based on:
     * - Task completion rate
     * - On-time delivery rate
     * - Dispute history
     */
    private Double calculateReliabilityScore(User user) {
        double completionRate = calculateCompletionRate(user);
        double onTimeRate = calculateOnTimeRate(user);
        double disputePenalty = calculateDisputePenalty(user);

        // Weighted combination
        double reliabilityRaw = (completionRate * 0.4) + (onTimeRate * 0.4) + ((100 - disputePenalty) * 0.2);
        
        return Math.max(0, Math.min(100, reliabilityRaw));
    }

    /**
     * Calculate final weighted score
     */
    private double calculateFinalScore(
            Double aiScore, Double creatorScore, Double reliabilityScore,
            double aiWeight, double creatorWeight, double reliabilityWeight) {
        
        double ai = aiScore != null ? aiScore : 70.0;
        double creator = creatorScore != null ? creatorScore : 70.0;
        double reliability = reliabilityScore != null ? reliabilityScore : 70.0;
        
        return (ai * aiWeight) + (creator * creatorWeight) + (reliability * reliabilityWeight);
    }

    /**
     * Calculate average divergence between AI scores and creator ratings
     */
    private Double calculateAverageDivergence(User user) {
        // Get average AI score
        Double avgAI = submissionRepository.getAverageAIScoreForUser(user);
        // Get average creator rating (convert to 0-100)
        Double avgCreator = taskRatingRepository.getOverallAverageRating(user);
        
        if (avgAI == null || avgCreator == null) {
            return null;
        }
        
        double creatorNormalized = avgCreator * 20.0;
        return Math.abs(avgAI - creatorNormalized);
    }

    /**
     * Calculate task completion rate
     */
    private double calculateCompletionRate(User user) {
        long assigned = taskRepository.countByAssigneeAndStatusIn(
                user, 
                java.util.List.of(
                        com.credbuzz.entity.TaskStatus.ASSIGNED,
                        com.credbuzz.entity.TaskStatus.IN_PROGRESS,
                        com.credbuzz.entity.TaskStatus.SUBMITTED,
                        com.credbuzz.entity.TaskStatus.IN_REVIEW,
                        com.credbuzz.entity.TaskStatus.COMPLETED,
                        com.credbuzz.entity.TaskStatus.APPROVED
                ));
        
        if (assigned == 0) return 100.0; // No tasks = 100% (neutral)
        
        long completed = taskRepository.countByAssigneeAndStatus(
                user, com.credbuzz.entity.TaskStatus.COMPLETED);
        
        return (completed * 100.0) / assigned;
    }

    /**
     * Calculate on-time delivery rate
     */
    private double calculateOnTimeRate(User user) {
        // Get submissions with deadline compliance score >= 70
        // For now, use AI deadline compliance average
        // TODO: Implement actual deadline tracking query
        return 85.0; // Placeholder - assume good until tracked
    }

    /**
     * Calculate dispute penalty (percentage points to deduct)
     */
    private double calculateDisputePenalty(User user) {
        long totalDisputes = disputeRepository.countByFiledAgainst(user);
        if (totalDisputes == 0) return 0.0;

        long lostDisputes = disputeRepository.countResolvedAgainstUser(user);
        
        // Each lost dispute = 10 point penalty, max 50
        return Math.min(lostDisputes * 10.0, 50.0);
    }

    /**
     * Calculate dispute loss rate
     */
    private double calculateDisputeLossRate(User user) {
        long total = disputeRepository.countByFiledAgainst(user);
        if (total == 0) return 0.0;
        
        long lost = disputeRepository.countResolvedAgainstUser(user);
        return (lost * 100.0) / total;
    }

    /**
     * Get completed task count for a user
     */
    private int getCompletedTaskCount(User user) {
        return (int) taskRepository.countByAssigneeAndStatus(
                user, com.credbuzz.entity.TaskStatus.COMPLETED);
    }

    // ============================================
    // PUBLIC QUERY METHODS
    // ============================================

    /**
     * Get simple reputation score (for lists/summaries)
     */
    public double getSimpleReputationScore(User user) {
        ReputationScore score = calculateReputation(user);
        return score.getFinalScore();
    }

    /**
     * Get reputation tier
     */
    public ReputationTier getReputationTier(User user) {
        ReputationScore score = calculateReputation(user);
        return score.getTier();
    }

    /**
     * Check if user meets minimum reputation threshold
     */
    public boolean meetsReputationThreshold(User user, double threshold) {
        return getSimpleReputationScore(user) >= threshold;
    }
}
