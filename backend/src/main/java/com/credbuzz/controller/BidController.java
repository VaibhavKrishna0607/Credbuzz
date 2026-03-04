package com.credbuzz.controller;

import com.credbuzz.dto.*;
import com.credbuzz.entity.User;
import com.credbuzz.service.BidService;
import com.credbuzz.service.BidEvaluationService;
import com.credbuzz.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================
 * Bid Controller - API Endpoints
 * ============================================
 * 
 * Handles all bid-related HTTP endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final UserService userService;
    private final BidEvaluationService bidEvaluationService;

    /**
     * Place a bid on a task
     * 
     * POST /api/tasks/{taskId}/bids
     */
    @PostMapping("/tasks/{taskId}/bids")
    public ResponseEntity<ApiResponse<BidDto>> createBid(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateBidRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            BidDto bid = bidService.createBid(taskId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("Bid placed successfully", bid));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all bids for a task
     * 
     * GET /api/tasks/{taskId}/bids
     */
    @GetMapping("/tasks/{taskId}/bids")
    public ResponseEntity<ApiResponse<List<BidDto>>> getBidsForTask(@PathVariable Long taskId) {
        try {
            List<BidDto> bids = bidService.getBidsForTask(taskId);
            return ResponseEntity.ok(ApiResponse.success(bids));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get ranked bids for a task (with AI scores)
     * 
     * GET /api/tasks/{taskId}/bids/ranked
     */
    @GetMapping("/tasks/{taskId}/bids/ranked")
    public ResponseEntity<ApiResponse<List<BidScoreDto>>> getRankedBids(@PathVariable Long taskId) {
        try {
            Long userId = getCurrentUserId();
            // Only allow the task poster (creator) or admin (future) to view ranked bids
            if (!bidService.isTaskPosterOrAdmin(taskId, userId)) {
                return ResponseEntity.status(403).body(ApiResponse.error("Forbidden: Only the task creator can view ranked bids."));
            }
            List<BidScoreDto> rankedBids = bidService.getRankedBids(taskId);
            return ResponseEntity.ok(ApiResponse.success(rankedBids));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all bids placed by the current user
     * 
     * GET /api/bids/my
     */
    @GetMapping("/bids/my")
    public ResponseEntity<ApiResponse<List<BidDto>>> getMyBids() {
        try {
            Long userId = getCurrentUserId();
            List<BidDto> bids = bidService.getMyBids(userId);
            return ResponseEntity.ok(ApiResponse.success(bids));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get a specific bid
     * 
     * GET /api/bids/{bidId}
     */
    @GetMapping("/bids/{bidId}")
    public ResponseEntity<ApiResponse<BidDto>> getBid(@PathVariable Long bidId) {
        try {
            BidDto bid = bidService.getBid(bidId);
            return ResponseEntity.ok(ApiResponse.success(bid));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Select a bid (assign task to bidder)
     * Only task poster can do this
     * 
     * PUT /api/bids/{bidId}/select
     */
    @PutMapping("/bids/{bidId}/select")
    public ResponseEntity<ApiResponse<BidDto>> selectBid(@PathVariable Long bidId) {
        try {
            Long userId = getCurrentUserId();
            BidDto bid = bidService.selectBid(bidId, userId);
            return ResponseEntity.ok(ApiResponse.success("Bid selected and task assigned", bid));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Update your bid
     * 
     * PUT /api/bids/{bidId}
     */
    @PutMapping("/bids/{bidId}")
    public ResponseEntity<ApiResponse<BidDto>> updateBid(
            @PathVariable Long bidId,
            @Valid @RequestBody CreateBidRequest request
    ) {
        try {
            Long userId = getCurrentUserId();
            BidDto bid = bidService.updateBid(bidId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("Bid updated successfully", bid));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Withdraw/delete your bid
     * 
     * DELETE /api/bids/{bidId}
     */
    @DeleteMapping("/bids/{bidId}")
    public ResponseEntity<ApiResponse<String>> deleteBid(@PathVariable Long bidId) {
        try {
            Long userId = getCurrentUserId();
            bidService.deleteBid(bidId, userId);
            return ResponseEntity.ok(ApiResponse.success("Bid withdrawn successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
