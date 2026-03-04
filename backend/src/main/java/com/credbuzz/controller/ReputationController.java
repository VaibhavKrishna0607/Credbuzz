package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.dto.ReputationScore;
import com.credbuzz.entity.User;
import com.credbuzz.repository.UserRepository;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.ReputationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Reputation Controller - Get user reputation scores
 */
@RestController
@RequestMapping("/api/reputation")
@RequiredArgsConstructor
@Slf4j
public class ReputationController {

    private final ReputationService reputationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Get reputation score for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getUserReputation(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ReputationScore score = reputationService.calculateReputation(user);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(score)
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get my reputation score
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getMyReputation(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ReputationScore score = reputationService.calculateReputation(user);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(score)
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Get simple reputation score (just the number)
     */
    @GetMapping("/user/{userId}/simple")
    public ResponseEntity<ApiResponse> getSimpleReputation(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            double score = reputationService.getSimpleReputationScore(user);
            ReputationScore.ReputationTier tier = reputationService.getReputationTier(user);

            return ResponseEntity.ok(ApiResponse.builder()
                    .success(true)
                    .data(java.util.Map.of(
                            "score", score,
                            "tier", tier.name(),
                            "tierDisplay", tier.getDisplayName()
                    ))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }
}
