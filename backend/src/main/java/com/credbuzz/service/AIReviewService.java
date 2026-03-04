package com.credbuzz.service;

import com.credbuzz.dto.AIReviewResult;
import com.credbuzz.entity.*;
import com.credbuzz.repository.BidRepository;
import com.credbuzz.repository.SubmissionRepository;
import com.credbuzz.repository.TaskRepository;
import com.credbuzz.service.ml.TextAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * AI Review Service - Performs objective assessment of work submissions.
 * 
 * Evaluates submissions against:
 * 1. Original task requirements
 * 2. Bidder's proposal/promise
 * 3. Technical quality indicators
 * 4. Deadline compliance
 * 
 * This provides an objective layer to balance creator's subjective rating.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIReviewService {
    /**
     * Perform AI review for a bid (not submission).
     * Used for objective assessment before assignment.
     */
    @Transactional
    public AIReviewResult reviewBidForTask(Bid bid, Task task) {
        // Use proposal message as submission content
        String submissionText = bid.getProposalMessage();
        String taskDescription = task.getDescription();
        List<String> taskSkills = task.getSkills();
        String proposalText = bid.getProposalMessage();

        // Calculate scores using bid-specific methods
        double requirementCoverage = calculateRequirementCoverageForBid(taskDescription, taskSkills, submissionText);
        double proposalAlignment = 85.0; // Bid is the proposal itself, so high alignment
        double technicalScore = calculateTechnicalScoreForBid(submissionText);
        double deadlineCompliance = calculateDeadlineComplianceForBid(task, bid);
        double finalScore = calculateFinalScore(requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance);
        String analysis = generateAnalysis(requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance);
        String concerns = identifyConcernsForBid(requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance, submissionText);

        boolean passesQuality = finalScore >= qualityThreshold;

        return AIReviewResult.builder()
                .requirementCoverage(requirementCoverage)
                .proposalAlignment(proposalAlignment)
                .technicalScore(technicalScore)
                .deadlineCompliance(deadlineCompliance)
                .finalScore(finalScore)
                .analysis(analysis)
                .concerns(concerns)
                .passesQualityThreshold(passesQuality)
                .qualityThreshold(qualityThreshold)
                .build();
    }

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final BidRepository bidRepository;
    private final TextAnalysisService textAnalysisService;

    @Value("${ai.review.quality-threshold:60.0}")
    private double qualityThreshold;

    /**
     * Perform AI review of a submission.
     * Analyzes submission content against task requirements and proposal.
     */
    @Transactional
    public AIReviewResult reviewSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        Task task = submission.getTask();
        User bidder = submission.getSubmitter();

        // Get the winning bid for proposal comparison
        Optional<Bid> winningBid = bidRepository.findByTaskAndSelected(task, true);
        String proposalText = winningBid.map(Bid::getProposalMessage).orElse("");

        // Calculate component scores
        double requirementCoverage = calculateRequirementCoverage(
                task.getDescription(), task.getSkills(), submission);
        
        double proposalAlignment = calculateProposalAlignment(
                proposalText, submission);
        
        double technicalScore = calculateTechnicalScore(submission);
        
        double deadlineCompliance = calculateDeadlineCompliance(task, submission);

        // Calculate final weighted score
        double finalScore = calculateFinalScore(
                requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance);

        // Generate analysis summary
        String analysis = generateAnalysis(
                requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance);
        
        String concerns = identifyConcerns(
                requirementCoverage, proposalAlignment, technicalScore, deadlineCompliance, submission);

        // Build result
        AIReviewResult result = AIReviewResult.builder()
                .requirementCoverage(requirementCoverage)
                .proposalAlignment(proposalAlignment)
                .technicalScore(technicalScore)
                .deadlineCompliance(deadlineCompliance)
                .finalScore(finalScore)
                .analysis(analysis)
                .concerns(concerns)
                .passesQualityThreshold(finalScore >= qualityThreshold)
                .qualityThreshold(qualityThreshold)
                .build();

        // Update submission with AI scores
        submission.setAiRequirementCoverage(requirementCoverage);
        submission.setAiProposalAlignment(proposalAlignment);
        submission.setAiTechnicalScore(technicalScore);
        submission.setAiDeadlineCompliance(deadlineCompliance);
        submission.setAiFinalScore(finalScore);
        submission.setAiReviewedAt(LocalDateTime.now());
        submission.setStatus(SubmissionStatus.PENDING_CREATOR_REVIEW);
        submissionRepository.save(submission);

        // Update task status to IN_REVIEW
        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);

        log.info("AI Review completed for submission {}. Final score: {:.2f}, Passes threshold: {}", 
                submissionId, finalScore, result.getPassesQualityThreshold());

        return result;
    }

    /**
     * Calculate how well submission covers task requirements.
     * Uses text analysis to compare submission against task description and skills.
     */
    private double calculateRequirementCoverage(
            String taskDescription, List<String> taskSkills, Submission submission) {
        
        String submissionText = buildSubmissionText(submission);
        
        try {
            // Use text analysis service for semantic similarity
            var analysisResult = textAnalysisService.analyzeTextRelevance(
                    submissionText, taskDescription);
            
            double semanticSimilarity = analysisResult.getSimilarityScore() * 100;
            
            // Check keyword coverage for required skills
            double keywordCoverage = calculateKeywordCoverage(submissionText, taskSkills);
            
            // Weighted combination (60% semantic, 40% keyword)
            return (semanticSimilarity * 0.6) + (keywordCoverage * 0.4);
            
        } catch (Exception e) {
            log.warn("Text analysis failed, falling back to keyword-based scoring: {}", e.getMessage());
            return calculateKeywordCoverage(submissionText, taskSkills);
        }
    }

    /**
     * Calculate alignment with bidder's original proposal.
     */
    private double calculateProposalAlignment(String proposalText, Submission submission) {
        if (proposalText == null || proposalText.isBlank()) {
            return 75.0; // Default if no proposal
        }

        String submissionText = buildSubmissionText(submission);
        
        try {
            var analysisResult = textAnalysisService.analyzeTextRelevance(
                    submissionText, proposalText);
            
            return analysisResult.getSimilarityScore() * 100;
            
        } catch (Exception e) {
            log.warn("Proposal alignment analysis failed: {}", e.getMessage());
            return 70.0; // Safe default
        }
    }

    /**
     * Calculate technical quality score based on submission characteristics.
     */
    private double calculateTechnicalScore(Submission submission) {
        double score = 50.0; // Base score

        // Bonus for having deliverables
        if (submission.getDeliverables() != null && !submission.getDeliverables().isEmpty()) {
            score += 15.0;
            // Additional bonus for multiple deliverables
            score += Math.min(submission.getDeliverables().size() * 3, 10);
        }

        // Bonus for evidence
        if (submission.getEvidence() != null && !submission.getEvidence().isEmpty()) {
            score += 10.0;
        }

        // Bonus for detailed completion notes
        if (submission.getCompletionNotes() != null) {
            int noteLength = submission.getCompletionNotes().length();
            if (noteLength > 100) score += 5.0;
            if (noteLength > 300) score += 5.0;
            if (noteLength > 500) score += 5.0;
        }

        // Bonus for detailed content
        if (submission.getContent() != null) {
            int contentLength = submission.getContent().length();
            if (contentLength > 200) score += 5.0;
            if (contentLength > 500) score += 5.0;
        }

        return Math.min(score, 100.0);
    }

    /**
     * Calculate deadline compliance score.
     * 100 = on time or early
     * 0 = significantly late
     */
    private double calculateDeadlineCompliance(Task task, Submission submission) {
        if (task.getDeadline() == null) {
            return 85.0; // No deadline = assume compliant
        }

        LocalDateTime deadline = task.getDeadline();
        LocalDateTime submittedAt = submission.getSubmittedAt();

        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }

        long hoursUntilDeadline = ChronoUnit.HOURS.between(submittedAt, deadline);

        if (hoursUntilDeadline >= 24) {
            return 100.0; // Submitted 1+ day early
        } else if (hoursUntilDeadline >= 0) {
            return 90.0 + (hoursUntilDeadline / 24.0 * 10.0); // On time
        } else if (hoursUntilDeadline >= -24) {
            return 70.0 + ((24 + hoursUntilDeadline) / 24.0 * 20.0); // Up to 1 day late
        } else if (hoursUntilDeadline >= -72) {
            return 40.0 + ((72 + hoursUntilDeadline) / 48.0 * 30.0); // Up to 3 days late
        } else {
            // More than 3 days late
            return Math.max(0.0, 40.0 - Math.abs(hoursUntilDeadline / 24.0) * 5);
        }
    }

    /**
     * Calculate final weighted AI score.
     */
    private double calculateFinalScore(
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance) {
        
        // Weights matching SubmissionService
        return (requirementCoverage * 0.35) +
               (proposalAlignment * 0.25) +
               (technicalScore * 0.25) +
               (deadlineCompliance * 0.15);
    }

    /**
     * Generate analysis summary text.
     */
    private String generateAnalysis(
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance) {
        
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("Requirement Coverage: ");
        analysis.append(getScoreDescription(requirementCoverage));
        analysis.append(". ");
        
        analysis.append("Proposal Alignment: ");
        analysis.append(getScoreDescription(proposalAlignment));
        analysis.append(". ");
        
        analysis.append("Technical Quality: ");
        analysis.append(getScoreDescription(technicalScore));
        analysis.append(". ");
        
        analysis.append("Deadline Compliance: ");
        analysis.append(getScoreDescription(deadlineCompliance));
        analysis.append(".");
        
        return analysis.toString();
    }

    /**
     * Get human-readable description of score.
     */
    private String getScoreDescription(double score) {
        if (score >= 90) return "Excellent";
        if (score >= 80) return "Good";
        if (score >= 70) return "Satisfactory";
        if (score >= 60) return "Needs Improvement";
        if (score >= 50) return "Below Expectations";
        return "Poor";
    }

    /**
     * Identify concerns based on scores.
     */
    private String identifyConcerns(
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance,
            Submission submission) {
        
        StringBuilder concerns = new StringBuilder();
        
        if (requirementCoverage < 60) {
            concerns.append("- Submission may not fully address task requirements. ");
        }
        if (proposalAlignment < 60) {
            concerns.append("- Work may differ significantly from original proposal. ");
        }
        if (technicalScore < 60) {
            concerns.append("- Limited deliverables or documentation provided. ");
        }
        if (deadlineCompliance < 60) {
            concerns.append("- Submission was significantly late. ");
        }
        if (submission.getDeliverables() == null || submission.getDeliverables().isEmpty()) {
            concerns.append("- No deliverable links/files provided. ");
        }
        
        return concerns.length() > 0 ? concerns.toString().trim() : "None";
    }

    /**
     * Build combined text from submission for analysis.
     */
    private String buildSubmissionText(Submission submission) {
        StringBuilder text = new StringBuilder();
        
        if (submission.getContent() != null) {
            text.append(submission.getContent()).append(" ");
        }
        if (submission.getCompletionNotes() != null) {
            text.append(submission.getCompletionNotes()).append(" ");
        }
        
        return text.toString().trim();
    }

    /**
     * Calculate keyword coverage percentage.
     */
    private double calculateKeywordCoverage(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 75.0; // Default if no keywords
        }
        
        String lowerText = text.toLowerCase();
        int found = 0;
        
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                found++;
            }
        }
        
        return (found * 100.0) / keywords.size();
    }

    /**
     * Calculate requirement coverage for a bid (not submission).
     */
    private double calculateRequirementCoverageForBid(
            String taskDescription, List<String> taskSkills, String proposalText) {
        
        try {
            // Use text analysis service for semantic similarity
            var analysisResult = textAnalysisService.analyzeTextRelevance(
                    proposalText, taskDescription);
            
            double semanticSimilarity = analysisResult.getSimilarityScore() * 100;
            
            // Check keyword coverage for required skills
            double keywordCoverage = calculateKeywordCoverage(proposalText, taskSkills);
            
            // Weighted combination (60% semantic, 40% keyword)
            return (semanticSimilarity * 0.6) + (keywordCoverage * 0.4);
            
        } catch (Exception e) {
            log.warn("Text analysis failed, falling back to keyword-based scoring: {}", e.getMessage());
            return calculateKeywordCoverage(proposalText, taskSkills);
        }
    }

    /**
     * Calculate technical score for a bid proposal.
     */
    private double calculateTechnicalScoreForBid(String proposalText) {
        double score = 50.0; // Base score

        if (proposalText == null || proposalText.isBlank()) {
            return 30.0;
        }

        // Bonus for detailed proposal
        int wordCount = proposalText.split("\\s+").length;
        if (wordCount > 100) {
            score += 20.0;
        } else if (wordCount > 50) {
            score += 10.0;
        }

        // Bonus for technical keywords
        String lowerText = proposalText.toLowerCase();
        if (lowerText.contains("experience") || lowerText.contains("portfolio")) {
            score += 10.0;
        }
        if (lowerText.contains("approach") || lowerText.contains("methodology")) {
            score += 10.0;
        }

        return Math.min(score, 100.0);
    }

    /**
     * Calculate deadline compliance for a bid.
     */
    private double calculateDeadlineComplianceForBid(Task task, Bid bid) {
        if (task.getDeadline() == null) {
            return 85.0; // No deadline = assume compliant
        }

        if (bid.getProposedCompletionDays() == null) {
            return 75.0; // No proposed deadline
        }

        LocalDateTime taskDeadline = task.getDeadline();
        LocalDateTime bidCreatedAt = bid.getCreatedAt();
        LocalDateTime proposedDeadline = bidCreatedAt.plusDays(bid.getProposedCompletionDays());

        long daysDifference = ChronoUnit.DAYS.between(proposedDeadline, taskDeadline);

        if (daysDifference >= 0) {
            // Proposed deadline is before or equal to task deadline (good)
            return 100.0;
        } else if (daysDifference >= -3) {
            // Up to 3 days late
            return 80.0 + (daysDifference * 6.67);
        } else {
            // More than 3 days late
            return Math.max(50.0, 80.0 + (daysDifference * 3.33));
        }
    }

    /**
     * Identify concerns for a bid proposal.
     */
    private String identifyConcernsForBid(
            double requirementCoverage,
            double proposalAlignment,
            double technicalScore,
            double deadlineCompliance,
            String proposalText) {
        
        StringBuilder concerns = new StringBuilder();
        
        if (requirementCoverage < 60) {
            concerns.append("- Proposal may not fully address task requirements. ");
        }
        if (technicalScore < 60) {
            concerns.append("- Limited detail or experience mentioned in proposal. ");
        }
        if (deadlineCompliance < 60) {
            concerns.append("- Proposed timeline may not meet task deadline. ");
        }
        if (proposalText == null || proposalText.length() < 50) {
            concerns.append("- Very brief proposal with minimal details. ");
        }
        
        return concerns.length() > 0 ? concerns.toString().trim() : "None";
    }

    /**
     * Process all pending AI reviews (batch job)
     */
    @Transactional
    public void processPendingReviews() {
        List<Submission> pending = submissionRepository
                .findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING_AI_REVIEW);
        
        log.info("Processing {} pending AI reviews", pending.size());
        
        for (Submission submission : pending) {
            try {
                reviewSubmission(submission.getId());
            } catch (Exception e) {
                log.error("Failed to review submission {}: {}", submission.getId(), e.getMessage());
            }
        }
    }
}
