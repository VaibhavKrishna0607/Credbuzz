package com.credbuzz.controller;

import com.credbuzz.dto.ApiResponse;
import com.credbuzz.entity.AuctionHistory;
import com.credbuzz.repository.AuctionHistoryRepository;
import com.credbuzz.repository.UserPerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ============================================
 * Admin Controller
 * ============================================
 * 
 * Admin-only endpoints for analytics and ML dataset export.
 * These endpoints should be protected with admin role in production.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuctionHistoryRepository auctionHistoryRepository;
    private final UserPerformanceRepository userPerformanceRepository;

    /**
     * Export auction dataset as CSV for ML model training
     * 
     * GET /api/admin/export-auction-dataset
     * 
     * Returns CSV containing:
     * - skillMatchScore
     * - completionRate
     * - creditDelta
     * - deadlineDelta
     * - avgRating
     * - workloadScore
     * - onTimeRate
     * - bidWinRate
     * - heuristicScore
     * - wasSelected
     * - completedSuccessfully
     */
    @GetMapping("/export-auction-dataset")
    public ResponseEntity<byte[]> exportAuctionDataset() {
        List<AuctionHistory> histories = auctionHistoryRepository.findAll();
        
        StringBuilder csv = new StringBuilder();
        
        // CSV Header
        csv.append("id,taskId,bidId,bidderId,posterId,");
        csv.append("taskTitle,taskCategory,originalCredits,taskSkillCount,");
        csv.append("proposedCredits,proposedCompletionDays,");
        csv.append("skillMatchScore,completionRate,creditDelta,deadlineDelta,");
        csv.append("avgRating,workloadScore,onTimeRate,bidWinRate,heuristicScore,");
        csv.append("wasSelected,completedSuccessfully,wasOnTime,actualCompletionDays,posterRating,");
        csv.append("auctionClosedAt,taskCompletedAt\n");
        
        // CSV Data
        for (AuctionHistory h : histories) {
            csv.append(nullSafe(h.getId())).append(",");
            csv.append(nullSafe(h.getTaskId())).append(",");
            csv.append(nullSafe(h.getBidId())).append(",");
            csv.append(nullSafe(h.getBidderId())).append(",");
            csv.append(nullSafe(h.getPosterId())).append(",");
            csv.append(escapeCSV(h.getTaskTitle())).append(",");
            csv.append(escapeCSV(h.getTaskCategory())).append(",");
            csv.append(nullSafe(h.getOriginalCredits())).append(",");
            csv.append(nullSafe(h.getTaskSkillCount())).append(",");
            csv.append(nullSafe(h.getProposedCredits())).append(",");
            csv.append(nullSafe(h.getProposedCompletionDays())).append(",");
            csv.append(formatDouble(h.getSkillMatchScore())).append(",");
            csv.append(formatDouble(h.getCompletionRate())).append(",");
            csv.append(formatDouble(h.getCreditDelta())).append(",");
            csv.append(formatDouble(h.getDeadlineDelta())).append(",");
            csv.append(formatDouble(h.getAvgRating())).append(",");
            csv.append(formatDouble(h.getWorkloadScore())).append(",");
            csv.append(formatDouble(h.getOnTimeRate())).append(",");
            csv.append(formatDouble(h.getBidWinRate())).append(",");
            csv.append(formatDouble(h.getHeuristicScore())).append(",");
            csv.append(h.getWasSelected() ? "1" : "0").append(",");
            csv.append(h.getCompletedSuccessfully() != null && h.getCompletedSuccessfully() ? "1" : "0").append(",");
            csv.append(h.getWasOnTime() != null ? (h.getWasOnTime() ? "1" : "0") : "").append(",");
            csv.append(nullSafe(h.getActualCompletionDays())).append(",");
            csv.append(formatDouble(h.getPosterRating())).append(",");
            csv.append(formatDateTime(h.getAuctionClosedAt())).append(",");
            csv.append(formatDateTime(h.getTaskCompletedAt())).append("\n");
        }
        
        byte[] csvBytes = csv.toString().getBytes();
        
        String filename = "auction_dataset_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csvBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    /**
     * Export training data only (completed auctions with outcomes)
     * 
     * GET /api/admin/export-training-data
     */
    @GetMapping("/export-training-data")
    public ResponseEntity<byte[]> exportTrainingData() {
        List<AuctionHistory> histories = auctionHistoryRepository.findTrainingData();
        
        StringBuilder csv = new StringBuilder();
        
        // CSV Header - Only ML features and target
        csv.append("skillMatchScore,completionRate,creditDelta,deadlineDelta,");
        csv.append("avgRating,workloadScore,onTimeRate,bidWinRate,");
        csv.append("completedSuccessfully\n");
        
        for (AuctionHistory h : histories) {
            csv.append(formatDouble(h.getSkillMatchScore())).append(",");
            csv.append(formatDouble(h.getCompletionRate())).append(",");
            csv.append(formatDouble(h.getCreditDelta())).append(",");
            csv.append(formatDouble(h.getDeadlineDelta())).append(",");
            csv.append(formatDouble(h.getAvgRating())).append(",");
            csv.append(formatDouble(h.getWorkloadScore())).append(",");
            csv.append(formatDouble(h.getOnTimeRate())).append(",");
            csv.append(formatDouble(h.getBidWinRate())).append(",");
            csv.append(h.getCompletedSuccessfully() ? "1" : "0").append("\n");
        }
        
        byte[] csvBytes = csv.toString().getBytes();
        
        String filename = "training_data_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csvBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    /**
     * Get auction statistics
     * 
     * GET /api/admin/auction-stats
     */
    @GetMapping("/auction-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuctionStats() {
        long totalAuctions = auctionHistoryRepository.countByWasSelectedTrue();
        long completedSuccessfully = auctionHistoryRepository.countByCompletedSuccessfullyTrue();
        long totalRecords = auctionHistoryRepository.count();
        
        Map<String, Object> stats = Map.of(
            "totalAuctionRecords", totalRecords,
            "totalAuctionsClosed", totalAuctions,
            "successfulCompletions", completedSuccessfully,
            "successRate", totalAuctions > 0 ? (double) completedSuccessfully / totalAuctions : 0
        );
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // Helper methods for CSV formatting
    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String formatDouble(Double value) {
        if (value == null) return "";
        return String.format("%.4f", value);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        // Escape quotes and wrap in quotes if contains comma or quote
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
