package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.entity.TaskRating;
import com.credbuzz.entity.User;
import com.credbuzz.repository.UserRepository;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.RatingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rating Controller - Handles task ratings and reviews
 */
@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
@Slf4j
public class RatingController {

    private final RatingService ratingService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Approve task with rating (combined action)
     */
    @PostMapping("/approve-with-rating")
    public ResponseEntity<ApiResponse> approveWithRating(
            @RequestBody ApproveWithRatingRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            TaskRating rating = ratingService.approveWithRating(
                    request.getTaskId(),
                    user,
                    request.getQualityScore(),
                    request.getCommunicationScore(),
                    request.getProfessionalismScore(),
                    request.getFeedback()
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Task approved with rating")
                    .data(toDto(rating))
                    .build());

        } catch (Exception e) {
            log.error("Error approving with rating: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get rating for a task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse> getTaskRating(@PathVariable Long taskId) {
        try {
            var rating = ratingService.getRatingForTask(taskId);
            
            if (rating.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.builder()
                        .success(true)
                        .message("No rating found")
                        .data(null)
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(toDto(rating.get()))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get all ratings for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getUserRatings(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<TaskRating> ratings = ratingService.getRatingsForUser(user);
            List<Map<String, Object>> dtos = ratings.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            Double avgRating = ratingService.getOverallAverageRating(user);
            long count = ratingService.getRatingCount(user);

            Map<String, Object> response = Map.of(
                    "ratings", dtos,
                    "averageRating", avgRating != null ? avgRating : 0.0,
                    "totalRatings", count
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(response)
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get review window time remaining
     */
    @GetMapping("/task/{taskId}/time-remaining")
    public ResponseEntity<ApiResponse> getReviewTimeRemaining(@PathVariable Long taskId) {
        try {
            long hoursRemaining = ratingService.getReviewWindowHoursRemaining(taskId);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(Map.of(
                            "hoursRemaining", hoursRemaining,
                            "reviewWindowHours", 48
                    ))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    private Map<String, Object> toDto(TaskRating r) {
        return Map.ofEntries(
                Map.entry("id", r.getId()),
                Map.entry("taskId", r.getTask().getId()),
                Map.entry("raterId", r.getRater().getId()),
                Map.entry("raterName", r.getRater().getName()),
                Map.entry("ratedUserId", r.getRatedUser().getId()),
                Map.entry("ratedUserName", r.getRatedUser().getName()),
                Map.entry("qualityScore", r.getQualityScore()),
                Map.entry("communicationScore", r.getCommunicationScore()),
                Map.entry("professionalismScore", r.getProfessionalismScore()),
                Map.entry("averageScore", r.getAverageScore()),
                Map.entry("feedback", r.getFeedback() != null ? r.getFeedback() : ""),
                Map.entry("aiScoreSnapshot", r.getAiScoreSnapshot() != null ? r.getAiScoreSnapshot() : 0.0),
                Map.entry("scoreDivergence", r.getScoreDivergence() != null ? r.getScoreDivergence() : 0.0),
                Map.entry("flaggedForReview", r.getFlaggedForReview() != null && r.getFlaggedForReview()),
                Map.entry("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
        );
    }

    @Data
    public static class ApproveWithRatingRequest {
        private Long taskId;
        private Integer qualityScore;
        private Integer communicationScore;
        private Integer professionalismScore;
        private String feedback;
    }
}
