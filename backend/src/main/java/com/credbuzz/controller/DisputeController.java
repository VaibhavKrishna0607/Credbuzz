package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.entity.*;
import com.credbuzz.repository.UserRepository;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.DisputeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispute Controller - Handles dispute filing and resolution
 */
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Slf4j
public class DisputeController {

    private final DisputeService disputeService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * File a new dispute
     */
    @PostMapping
    public ResponseEntity<ApiResponse> fileDispute(
            @RequestBody FileDisputeRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Dispute dispute = disputeService.fileDispute(
                    request.getTaskId(),
                    user,
                    request.getReason(),
                    request.getComplaint()
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Dispute filed successfully")
                    .data(toDto(dispute))
                    .build());

        } catch (Exception e) {
            log.error("Error filing dispute: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Respond to a dispute
     */
    @PostMapping("/{disputeId}/respond")
    public ResponseEntity<ApiResponse> respondToDispute(
            @PathVariable Long disputeId,
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String response = request.get("response");
            if (response == null || response.isBlank()) {
                throw new RuntimeException("Response is required");
            }

            Dispute dispute = disputeService.respondToDispute(disputeId, user, response);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Response submitted")
                    .data(toDto(dispute))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Cancel a dispute (filer only)
     */
    @PostMapping("/{disputeId}/cancel")
    public ResponseEntity<ApiResponse> cancelDispute(
            @PathVariable Long disputeId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Dispute dispute = disputeService.cancelDispute(disputeId, user);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Dispute cancelled")
                    .data(toDto(dispute))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get dispute by ID
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<ApiResponse> getDispute(@PathVariable Long disputeId) {
        try {
            Dispute dispute = disputeService.getDispute(disputeId)
                    .orElseThrow(() -> new RuntimeException("Dispute not found"));

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(toDto(dispute))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get dispute for a task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse> getDisputeByTask(@PathVariable Long taskId) {
        try {
            var dispute = disputeService.getDisputeByTaskId(taskId);
            
            if (dispute.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.builder()
                        .success(true)
                        .message("No dispute found")
                        .data(null)
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(toDto(dispute.get()))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get all disputes for current user
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse> getMyDisputes(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Dispute> disputes = disputeService.getUserDisputes(user);
            List<Map<String, Object>> dtos = disputes.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
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
     * Admin: Resolve a dispute
     */
    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<ApiResponse> resolveDispute(
            @PathVariable Long disputeId,
            @RequestBody ResolveDisputeRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User resolver = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // TODO: Add admin role check here
            
            Dispute dispute = disputeService.resolveDispute(
                    disputeId,
                    resolver,
                    request.getResolution(),
                    request.getBidderPercentage(),
                    request.getNotes()
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Dispute resolved")
                    .data(toDto(dispute))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    private Map<String, Object> toDto(Dispute d) {
        return Map.ofEntries(
                Map.entry("id", d.getId()),
                Map.entry("taskId", d.getTask().getId()),
                Map.entry("taskTitle", d.getTask().getTitle()),
                Map.entry("filedById", d.getFiledBy().getId()),
                Map.entry("filedByName", d.getFiledBy().getName()),
                Map.entry("filedAgainstId", d.getFiledAgainst().getId()),
                Map.entry("filedAgainstName", d.getFiledAgainst().getName()),
                Map.entry("status", d.getStatus().name()),
                Map.entry("reason", d.getReason().name()),
                Map.entry("complaint", d.getComplaint()),
                Map.entry("response", d.getResponse() != null ? d.getResponse() : ""),
                Map.entry("aiScoreSnapshot", d.getAiScoreSnapshot() != null ? d.getAiScoreSnapshot() : 0.0),
                Map.entry("resolution", d.getResolution() != null ? d.getResolution().name() : null),
                Map.entry("bidderPercentage", d.getBidderPercentage() != null ? d.getBidderPercentage() : null),
                Map.entry("resolutionNotes", d.getResolutionNotes() != null ? d.getResolutionNotes() : ""),
                Map.entry("filedAt", d.getFiledAt() != null ? d.getFiledAt().toString() : null),
                Map.entry("respondedAt", d.getRespondedAt() != null ? d.getRespondedAt().toString() : null),
                Map.entry("resolvedAt", d.getResolvedAt() != null ? d.getResolvedAt().toString() : null)
        );
    }

    @Data
    public static class FileDisputeRequest {
        private Long taskId;
        private DisputeReason reason;
        private String complaint;
    }

    @Data
    public static class ResolveDisputeRequest {
        private DisputeResolution resolution;
        private Integer bidderPercentage;
        private String notes;
    }
}
