package com.credbuzz.service;

import com.credbuzz.entity.*;
import com.credbuzz.repository.SubmissionRepository;
import com.credbuzz.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Submission Service - Handles work submissions and revision flow.
 * 
 * Flow:
 * 1. Bidder submits work with deliverables and evidence
 * 2. System triggers AI review
 * 3. Task moves to IN_REVIEW, awaiting creator decision
 * 4. Creator can: Approve / Request Revision / Dispute
 * 5. Max 2 revisions allowed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private static final int MAX_REVISIONS = 2;

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;

    /**
     * Create a new submission for a task.
     * Automatically determines version number.
     * 
     * @param taskId Task ID
     * @param submitter User submitting the work
     * @param content Main deliverable content
     * @param completionNotes Notes explaining what was done
     * @param deliverables List of deliverable URLs
     * @param evidence List of evidence URLs
     * @return Created submission
     */
    @Transactional
    public Submission createSubmission(
            Long taskId,
            User submitter,
            String content,
            String completionNotes,
            List<String> deliverables,
            List<String> evidence) {
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate submitter is the assignee
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(submitter.getId())) {
            throw new RuntimeException("Only the assignee can submit work");
        }

        // Validate task status allows submission
        if (task.getStatus() != TaskStatus.IN_PROGRESS && 
            task.getStatus() != TaskStatus.ASSIGNED &&
            task.getStatus() != TaskStatus.REVISION_REQUESTED) {
            throw new RuntimeException("Cannot submit work in status: " + task.getStatus());
        }

        // Check revision limit
        long existingSubmissions = submissionRepository.countByTaskId(taskId);
        if (existingSubmissions > MAX_REVISIONS) {
            throw new RuntimeException("Maximum revision limit (" + MAX_REVISIONS + ") reached");
        }

        // Determine version number
        int versionNumber = (int) existingSubmissions + 1;

        // Create submission
        Submission submission = Submission.builder()
                .task(task)
                .submitter(submitter)
                .versionNumber(versionNumber)
                .content(content)
                .completionNotes(completionNotes)
                .deliverables(deliverables != null ? deliverables : List.of())
                .evidence(evidence != null ? evidence : List.of())
                .status(SubmissionStatus.PENDING_AI_REVIEW)
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        // Update task status to SUBMITTED
        task.setStatus(TaskStatus.SUBMITTED);
        task.setSubmission(content); // Keep backward compatibility
        task.setSubmittedAt(LocalDateTime.now());
        taskRepository.save(task);

        log.info("Created submission v{} for task {} by user {}", 
                versionNumber, taskId, submitter.getName());

        return savedSubmission;
    }

    /**
     * Update submission with AI review results.
     * Called by AIReviewService after analysis.
     */
    @Transactional
    public Submission updateWithAIReview(
            Long submissionId,
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance) {
        
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // Calculate final AI score (weighted average)
        double finalScore = calculateFinalAIScore(
                requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance);

        submission.setAiRequirementCoverage(requirementCoverage);
        submission.setAiProposalAlignment(proposalAlignment);
        submission.setAiTechnicalScore(technicalScore);
        submission.setAiDeadlineCompliance(deadlineCompliance);
        submission.setAiFinalScore(finalScore);
        submission.setAiReviewedAt(LocalDateTime.now());
        submission.setStatus(SubmissionStatus.PENDING_CREATOR_REVIEW);

        Submission savedSubmission = submissionRepository.save(submission);

        // Update task to IN_REVIEW
        Task task = submission.getTask();
        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);

        log.info("AI review completed for submission {} with final score: {:.2f}", 
                submissionId, finalScore);

        return savedSubmission;
    }

    /**
     * Calculate weighted final AI score
     */
    private double calculateFinalAIScore(
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance) {
        
        // Weights: Requirements most important, deadline compliance least
        return (requirementCoverage * 0.35) +
               (proposalAlignment * 0.25) +
               (technicalScore * 0.25) +
               (deadlineCompliance * 0.15);
    }

    /**
     * Mark submission as approved by creator
     */
    @Transactional
    public Submission approveSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());

        return submissionRepository.save(submission);
    }

    /**
     * Mark submission as revision requested
     */
    @Transactional
    public Submission requestRevision(Long submissionId, String reason) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // Check revision limit
        long submissionCount = submissionRepository.countByTaskId(submission.getTask().getId());
        if (submissionCount > MAX_REVISIONS) {
            throw new RuntimeException("Maximum revision limit reached. Please approve or dispute.");
        }

        submission.setStatus(SubmissionStatus.REVISION_REQUESTED);
        submission.setReviewedAt(LocalDateTime.now());

        // Update task status
        Task task = submission.getTask();
        task.setStatus(TaskStatus.REVISION_REQUESTED);
        taskRepository.save(task);

        log.info("Revision requested for submission {}. Reason: {}", submissionId, reason);

        return submissionRepository.save(submission);
    }

    /**
     * Mark submission as disputed
     */
    @Transactional
    public Submission disputeSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        submission.setStatus(SubmissionStatus.DISPUTED);
        submission.setReviewedAt(LocalDateTime.now());

        // Update task status
        Task task = submission.getTask();
        task.setStatus(TaskStatus.DISPUTED);
        taskRepository.save(task);

        log.info("Submission {} marked as disputed", submissionId);

        return submissionRepository.save(submission);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public List<Submission> getTaskSubmissions(Long taskId) {
        return submissionRepository.findByTaskIdOrderByVersionNumberDesc(taskId);
    }

    public Optional<Submission> getLatestSubmission(Long taskId) {
        return submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId);
    }

    public Optional<Submission> getSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId);
    }

    public List<Submission> getUserSubmissions(User user) {
        return submissionRepository.findBySubmitter(user);
    }

    public List<Submission> getPendingAIReview() {
        return submissionRepository.findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING_AI_REVIEW);
    }

    public int getRevisionCount(Long taskId) {
        return (int) submissionRepository.countByTaskId(taskId) - 1;
    }

    public boolean canRequestMoreRevisions(Long taskId) {
        return submissionRepository.countByTaskId(taskId) <= MAX_REVISIONS;
    }

    public Double getAverageAIScoreForUser(User user) {
        return submissionRepository.getAverageAIScoreForUser(user);
    }
}
