package com.credbuzz.service;

import com.credbuzz.entity.User;
import com.credbuzz.entity.UserPerformance;
import com.credbuzz.repository.UserPerformanceRepository;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ============================================
 * User Performance Service
 * ============================================
 * 
 * Manages user performance metrics tracking.
 * Updates metrics when tasks are completed, abandoned, etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPerformanceService {

    private final UserPerformanceRepository performanceRepository;
    private final UserRepository userRepository;

    /**
     * Get or create performance record for a user
     */
    @Transactional
    public UserPerformance getOrCreatePerformance(Long userId) {
        return performanceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    
                    UserPerformance performance = UserPerformance.builder()
                            .user(user)
                            .tasksCompleted(0)
                            .tasksAssigned(0)
                            .tasksAbandoned(0)
                            .tasksRejected(0)
                            .onTimeCompletions(0)
                            .lateCompletions(0)
                            .avgCompletionDays(0.0)
                            .avgRating(0.0)
                            .totalRatings(0)
                            .activeTasksCount(0)
                            .totalBidsPlaced(0)
                            .bidsWon(0)
                            .avgBidCredits(0.0)
                            .skillVerificationScore(0)
                            .build();
                    
                    return performanceRepository.save(performance);
                });
    }

    /**
     * Record when a user is assigned a task (bid won)
     */
    @Transactional
    public void recordTaskAssigned(Long userId) {
        UserPerformance performance = getOrCreatePerformance(userId);
        performance.setTasksAssigned(performance.getTasksAssigned() + 1);
        performance.setActiveTasksCount(performance.getActiveTasksCount() + 1);
        performance.setBidsWon(performance.getBidsWon() + 1);
        performance.setLastActiveAt(LocalDateTime.now());
        performanceRepository.save(performance);
        log.info("Recorded task assignment for user {}", userId);
    }

    /**
     * Record when a user completes a task
     */
    @Transactional
    public void recordTaskCompleted(Long userId, int completionDays, boolean onTime) {
        UserPerformance performance = getOrCreatePerformance(userId);
        
        performance.setTasksCompleted(performance.getTasksCompleted() + 1);
        performance.setActiveTasksCount(Math.max(0, performance.getActiveTasksCount() - 1));
        
        if (onTime) {
            performance.setOnTimeCompletions(performance.getOnTimeCompletions() + 1);
        } else {
            performance.setLateCompletions(performance.getLateCompletions() + 1);
        }

        // Update average completion days
        double totalDays = performance.getAvgCompletionDays() * (performance.getTasksCompleted() - 1);
        performance.setAvgCompletionDays((totalDays + completionDays) / performance.getTasksCompleted());

        performance.setLastActiveAt(LocalDateTime.now());
        performanceRepository.save(performance);
        log.info("Recorded task completion for user {}", userId);
    }

    /**
     * Record when a user abandons a task
     */
    @Transactional
    public void recordTaskAbandoned(Long userId) {
        UserPerformance performance = getOrCreatePerformance(userId);
        performance.setTasksAbandoned(performance.getTasksAbandoned() + 1);
        performance.setActiveTasksCount(Math.max(0, performance.getActiveTasksCount() - 1));
        performanceRepository.save(performance);
        log.info("Recorded task abandonment for user {}", userId);
    }

    /**
     * Record when a user's submission is rejected
     */
    @Transactional
    public void recordSubmissionRejected(Long userId) {
        UserPerformance performance = getOrCreatePerformance(userId);
        performance.setTasksRejected(performance.getTasksRejected() + 1);
        performanceRepository.save(performance);
        log.info("Recorded submission rejection for user {}", userId);
    }

    /**
     * Record a rating given to user
     */
    @Transactional
    public void recordRating(Long userId, double rating) {
        UserPerformance performance = getOrCreatePerformance(userId);
        
        double totalRating = performance.getAvgRating() * performance.getTotalRatings();
        performance.setTotalRatings(performance.getTotalRatings() + 1);
        performance.setAvgRating((totalRating + rating) / performance.getTotalRatings());
        
        performanceRepository.save(performance);
        log.info("Recorded rating {} for user {}", rating, userId);
    }

    /**
     * Record when a user places a bid
     */
    @Transactional
    public void recordBidPlaced(Long userId, int proposedCredits) {
        UserPerformance performance = getOrCreatePerformance(userId);
        
        double totalCredits = performance.getAvgBidCredits() * performance.getTotalBidsPlaced();
        performance.setTotalBidsPlaced(performance.getTotalBidsPlaced() + 1);
        performance.setAvgBidCredits((totalCredits + proposedCredits) / performance.getTotalBidsPlaced());
        
        performance.setLastActiveAt(LocalDateTime.now());
        performanceRepository.save(performance);
    }

    /**
     * Update skill verification score
     */
    @Transactional
    public void updateSkillVerification(Long userId, int score) {
        UserPerformance performance = getOrCreatePerformance(userId);
        performance.setSkillVerificationScore(Math.min(100, Math.max(0, score)));
        performanceRepository.save(performance);
    }
}
