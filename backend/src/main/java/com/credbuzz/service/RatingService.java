package com.credbuzz.service;

import com.credbuzz.entity.*;
import com.credbuzz.repository.SubmissionRepository;
import com.credbuzz.repository.TaskRatingRepository;
import com.credbuzz.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Rating Service - Handles creator's subjective ratings and review window.
 * 
 * Features:
 * - 48-hour review window with auto-approve
 * - Rating validation (1-5 scale)
 * - Divergence detection with AI scores
 * - Abuse pattern detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private static final int REVIEW_WINDOW_HOURS = 48;
    private static final double HIGH_DIVERGENCE_THRESHOLD = 25.0;
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final TaskRatingRepository taskRatingRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final EscrowService escrowService;

    /**
     * Create a rating for a completed task.
     * Called when creator approves work with rating.
     */
    @Transactional
    public TaskRating createRating(
            Long taskId,
            User rater,
            int qualityScore,
            int communicationScore,
            int professionalismScore,
            String feedback) {
        
        // Validate scores
        validateScore(qualityScore, "Quality");
        validateScore(communicationScore, "Communication");
        validateScore(professionalismScore, "Professionalism");

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate rater is the task creator
        if (!task.getPoster().getId().equals(rater.getId())) {
            throw new RuntimeException("Only task creator can rate");
        }

        // Check task status allows rating
        if (task.getStatus() != TaskStatus.IN_REVIEW && 
            task.getStatus() != TaskStatus.SUBMITTED &&
            task.getStatus() != TaskStatus.APPROVED) {
            throw new RuntimeException("Cannot rate task in status: " + task.getStatus());
        }

        // Check if already rated
        if (taskRatingRepository.existsByTaskId(taskId)) {
            throw new RuntimeException("Task already has a rating");
        }

        // Get AI score from latest submission
        Double aiScore = getLatestAIScore(taskId);

        // Create rating
        TaskRating rating = TaskRating.builder()
                .task(task)
                .rater(rater)
                .ratedUser(task.getAssignee())
                .qualityScore(qualityScore)
                .communicationScore(communicationScore)
                .professionalismScore(professionalismScore)
                .feedback(feedback)
                .aiScoreSnapshot(aiScore)
                .build();

        TaskRating savedRating = taskRatingRepository.save(rating);

        log.info("Rating created for task {}: Q={}, C={}, P={}, Avg={:.2f}, AIScore={:.2f}, Divergence={:.2f}", 
                taskId, qualityScore, communicationScore, professionalismScore,
                savedRating.getAverageScore(), aiScore, savedRating.getScoreDivergence());

        if (Boolean.TRUE.equals(savedRating.getFlaggedForReview())) {
            log.warn("⚠️ FLAGGED: High divergence between creator rating and AI score for task {}", taskId);
        }

        return savedRating;
    }

    /**
     * Approve and complete task with rating.
     * Releases escrow and creates rating.
     */
    @Transactional
    public TaskRating approveWithRating(
            Long taskId,
            User creator,
            int qualityScore,
            int communicationScore,
            int professionalismScore,
            String feedback) {
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Create rating first
        TaskRating rating = createRating(
                taskId, creator, qualityScore, communicationScore, professionalismScore, feedback);

        // Update task status to APPROVED
        task.setStatus(TaskStatus.APPROVED);
        taskRepository.save(task);

        // Release escrow
        escrowService.releaseCredits(taskId);

        // Complete task
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        // Update latest submission status
        submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId)
                .ifPresent(submission -> {
                    submission.setStatus(SubmissionStatus.APPROVED);
                    submission.setReviewedAt(LocalDateTime.now());
                    submissionRepository.save(submission);
                });

        log.info("Task {} approved with rating and escrow released", taskId);

        return rating;
    }

    /**
     * Auto-approve tasks that have been in review for 48+ hours.
     * Scheduled job that runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void autoApproveExpiredReviewWindow() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(REVIEW_WINDOW_HOURS);
        
        List<Task> tasksInReview = taskRepository.findByStatus(TaskStatus.IN_REVIEW);
        
        for (Task task : tasksInReview) {
            Optional<Submission> latestSubmission = 
                    submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(task.getId());
            
            if (latestSubmission.isPresent()) {
                Submission submission = latestSubmission.get();
                
                // Check if AI review was completed more than 48 hours ago
                if (submission.getAiReviewedAt() != null && 
                    submission.getAiReviewedAt().isBefore(cutoff)) {
                    
                    try {
                        autoApproveTask(task, submission);
                        log.info("Auto-approved task {} after {} hour review window expired", 
                                task.getId(), REVIEW_WINDOW_HOURS);
                    } catch (Exception e) {
                        log.error("Failed to auto-approve task {}: {}", task.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Auto-approve a task with default ratings based on AI score.
     */
    private void autoApproveTask(Task task, Submission submission) {
        Double aiScore = submission.getAiFinalScore();
        
        // Calculate default ratings based on AI score (0-100 to 1-5 scale)
        int defaultRating = aiScore != null ? 
                (int) Math.round(aiScore / 20.0) : 4;
        defaultRating = Math.max(MIN_RATING, Math.min(MAX_RATING, defaultRating));

        // Create auto-rating
        TaskRating rating = TaskRating.builder()
                .task(task)
                .rater(task.getPoster())
                .ratedUser(task.getAssignee())
                .qualityScore(defaultRating)
                .communicationScore(defaultRating)
                .professionalismScore(defaultRating)
                .feedback("[Auto-approved] Review window expired without creator action.")
                .aiScoreSnapshot(aiScore)
                .build();

        taskRatingRepository.save(rating);

        // Update submission
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        // Release escrow
        escrowService.releaseCredits(task.getId());

        // Complete task
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        log.info("Task {} auto-approved with default rating {} based on AI score {:.2f}", 
                task.getId(), defaultRating, aiScore);
    }

    /**
     * Get hours remaining in review window.
     */
    public long getReviewWindowHoursRemaining(Long taskId) {
        Optional<Submission> submission = 
                submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId);
        
        if (submission.isPresent() && submission.get().getAiReviewedAt() != null) {
            LocalDateTime deadline = submission.get().getAiReviewedAt().plusHours(REVIEW_WINDOW_HOURS);
            return ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        }
        
        return REVIEW_WINDOW_HOURS;
    }

    /**
     * Validate rating score is within valid range.
     */
    private void validateScore(int score, String field) {
        if (score < MIN_RATING || score > MAX_RATING) {
            throw new RuntimeException(
                    field + " score must be between " + MIN_RATING + " and " + MAX_RATING);
        }
    }

    /**
     * Get the latest AI score for a task.
     */
    private Double getLatestAIScore(Long taskId) {
        return submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId)
                .map(Submission::getAiFinalScore)
                .orElse(null);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public Optional<TaskRating> getRatingForTask(Long taskId) {
        return taskRatingRepository.findByTaskId(taskId);
    }

    public List<TaskRating> getRatingsForUser(User user) {
        return taskRatingRepository.findByRatedUserOrderByCreatedAtDesc(user);
    }

    public Double getOverallAverageRating(User user) {
        return taskRatingRepository.getOverallAverageRating(user);
    }

    public long getRatingCount(User user) {
        return taskRatingRepository.countByRatedUser(user);
    }

    public List<TaskRating> getFlaggedRatings() {
        return taskRatingRepository.findByFlaggedForReviewTrue();
    }

    /**
     * Check if a rater shows patterns of abuse (consistently low ratings vs AI)
     */
    public boolean isRaterAbusive(User rater) {
        long flaggedCount = taskRatingRepository.countByRaterAndFlaggedForReviewTrue(rater);
        // Flag if more than 3 ratings show high divergence
        return flaggedCount > 3;
    }
}
