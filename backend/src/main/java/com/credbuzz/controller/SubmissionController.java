package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.dto.AIReviewResult;
import com.credbuzz.entity.Submission;
import com.credbuzz.entity.User;
import com.credbuzz.repository.UserRepository;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.AIReviewService;
import com.credbuzz.service.SubmissionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Submission Controller - Handles work submissions and reviews
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Slf4j
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AIReviewService aiReviewService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Create a new submission for a task
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createSubmission(
            @RequestBody CreateSubmissionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Submission submission = submissionService.createSubmission(
                    request.getTaskId(),
                    user,
                    request.getContent(),
                    request.getCompletionNotes(),
                    request.getDeliverables(),
                    request.getEvidence()
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Submission created successfully")
                    .data(toDto(submission))
                    .build());

        } catch (Exception e) {
            log.error("Error creating submission: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Trigger AI review for a submission
     */
    @PostMapping("/{submissionId}/review")
    public ResponseEntity<ApiResponse> triggerAIReview(
            @PathVariable Long submissionId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            AIReviewResult result = aiReviewService.reviewSubmission(submissionId);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("AI review completed")
                    .data(result)
                    .build());

        } catch (Exception e) {
            log.error("Error reviewing submission: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get all submissions for a task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse> getTaskSubmissions(
            @PathVariable Long taskId) {
        
        try {
            List<Submission> submissions = submissionService.getTaskSubmissions(taskId);
            List<Map<String, Object>> dtos = submissions.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Found " + submissions.size() + " submissions")
                    .data(dtos)
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get single submission
     */
    @GetMapping("/{submissionId}")
    public ResponseEntity<ApiResponse> getSubmission(
            @PathVariable Long submissionId) {
        
        try {
            Submission submission = submissionService.getSubmission(submissionId)
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(toDto(submission))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Request revision on a submission
     */
    @PostMapping("/{submissionId}/request-revision")
    public ResponseEntity<ApiResponse> requestRevision(
            @PathVariable Long submissionId,
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String reason = request.getOrDefault("reason", "Revision requested");
            Submission submission = submissionService.requestRevision(submissionId, reason);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Revision requested")
                    .data(toDto(submission))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    private Map<String, Object> toDto(Submission s) {
        return Map.ofEntries(
                Map.entry("id", s.getId()),
                Map.entry("taskId", s.getTask().getId()),
                Map.entry("submitterId", s.getSubmitter().getId()),
                Map.entry("submitterName", s.getSubmitter().getName()),
                Map.entry("versionNumber", s.getVersionNumber()),
                Map.entry("content", s.getContent() != null ? s.getContent() : ""),
                Map.entry("completionNotes", s.getCompletionNotes() != null ? s.getCompletionNotes() : ""),
                Map.entry("deliverables", s.getDeliverables() != null ? s.getDeliverables() : List.of()),
                Map.entry("evidence", s.getEvidence() != null ? s.getEvidence() : List.of()),
                Map.entry("status", s.getStatus().name()),
                Map.entry("aiFinalScore", s.getAiFinalScore() != null ? s.getAiFinalScore() : 0.0),
                Map.entry("aiRequirementCoverage", s.getAiRequirementCoverage() != null ? s.getAiRequirementCoverage() : 0.0),
                Map.entry("aiProposalAlignment", s.getAiProposalAlignment() != null ? s.getAiProposalAlignment() : 0.0),
                Map.entry("aiTechnicalScore", s.getAiTechnicalScore() != null ? s.getAiTechnicalScore() : 0.0),
                Map.entry("aiDeadlineCompliance", s.getAiDeadlineCompliance() != null ? s.getAiDeadlineCompliance() : 0.0),
                Map.entry("submittedAt", s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null),
                Map.entry("aiReviewedAt", s.getAiReviewedAt() != null ? s.getAiReviewedAt().toString() : null)
        );
    }

    @Data
    public static class CreateSubmissionRequest {
        private Long taskId;
        private String content;
        private String completionNotes;
        private List<String> deliverables;
        private List<String> evidence;
    }
}
