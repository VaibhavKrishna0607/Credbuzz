package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.entity.*;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Escrow Controller - Manages escrow queries (not modifications - those are internal)
 */
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
@Slf4j
public class EscrowController {

    private final EscrowService escrowService;
    private final JwtService jwtService;

    /**
     * Get escrow status for a task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse> getTaskEscrow(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            Optional<Escrow> escrow = escrowService.getEscrowByTaskId(taskId);
            
            if (escrow.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.builder()
                        .success(true)
                        .message("No escrow found for this task")
                        .data(null)
                        .build());
            }

            Escrow e = escrow.get();
            Map<String, Object> escrowDto = Map.of(
                    "id", e.getId(),
                    "taskId", e.getTask().getId(),
                    "lockedCredits", e.getLockedCredits(),
                    "releasedCredits", e.getReleasedCredits(),
                    "refundedCredits", e.getRefundedCredits(),
                    "status", e.getStatus().name(),
                    "lockedAt", e.getLockedAt() != null ? e.getLockedAt().toString() : null,
                    "resolvedAt", e.getResolvedAt() != null ? e.getResolvedAt().toString() : null
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Escrow found")
                    .data(escrowDto)
                    .build());

        } catch (Exception e) {
            log.error("Error fetching escrow: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get user's locked credits (as creator)
     */
    @GetMapping("/my-locked")
    public ResponseEntity<ApiResponse> getMyLockedCredits(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            
            // This would need user lookup - simplified for now
            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .message("Use /api/users/me for credit info")
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }
}
