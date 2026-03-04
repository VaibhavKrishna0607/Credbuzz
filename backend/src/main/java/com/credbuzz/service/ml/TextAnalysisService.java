package com.credbuzz.service.ml;

import com.credbuzz.config.MLConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * ============================================
 * Text Analysis Service
 * ============================================
 * 
 * Communicates with ML service for text-based features:
 * - proposalRelevanceScore: Semantic similarity between task description and proposal
 * - keywordCoverageScore: How well proposal covers task keywords
 * 
 * Endpoints:
 * POST /analyze-text - Single proposal analysis
 * POST /analyze-text/batch - Batch analysis for multiple proposals
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextAnalysisService {

    private final WebClient mlWebClient;
    private final MLConfig mlConfig;

    private static final int TIMEOUT_SECONDS = 10;
    
    // Default scores when text analysis is unavailable
    private static final double DEFAULT_RELEVANCE_SCORE = 0.5;
    private static final double DEFAULT_COVERAGE_SCORE = 0.5;

    /**
     * Analyze a single proposal against a task description
     */
    public TextAnalysisResult analyzeProposal(String taskDescription, List<String> taskSkills, String proposalText) {
        if (!mlConfig.isMlEnabled()) {
            log.debug("ML disabled, using default text scores");
            return TextAnalysisResult.defaults();
        }

        if (taskDescription == null || taskDescription.isEmpty() || 
            proposalText == null || proposalText.isEmpty()) {
            log.debug("Missing text content, using default scores");
            return TextAnalysisResult.defaults();
        }

        try {
            TextAnalysisRequest request = new TextAnalysisRequest();
            request.setTaskDescription(taskDescription);
            request.setTaskSkills(taskSkills);
            request.setProposalText(proposalText);

            TextAnalysisResponse response = mlWebClient.post()
                    .uri("/analyze-text")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TextAnalysisResponse.class)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .onErrorResume(ex -> {
                        log.warn("Text analysis failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                log.debug("Text analysis complete: relevance={}, coverage={}", 
                        response.getProposalRelevanceScore(), response.getKeywordCoverageScore());
                return TextAnalysisResult.from(response);
            }

        } catch (Exception e) {
            log.warn("Text analysis error: {}", e.getMessage());
        }

        return TextAnalysisResult.defaults();
    }

    /**
     * Batch analyze multiple proposals against a single task (more efficient)
     */
    public List<TextAnalysisResult> analyzeProposalsBatch(
            String taskDescription, 
            List<String> taskSkills, 
            List<String> proposals) {
        
        if (!mlConfig.isMlEnabled()) {
            return proposals.stream()
                    .map(p -> TextAnalysisResult.defaults())
                    .toList();
        }

        if (taskDescription == null || taskDescription.isEmpty()) {
            return proposals.stream()
                    .map(p -> TextAnalysisResult.defaults())
                    .toList();
        }

        try {
            BatchTextAnalysisRequest request = new BatchTextAnalysisRequest();
            request.setTaskDescription(taskDescription);
            request.setTaskSkills(taskSkills);
            request.setProposals(proposals);

            BatchTextAnalysisResponse response = mlWebClient.post()
                    .uri("/analyze-text/batch")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BatchTextAnalysisResponse.class)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS + proposals.size()))
                    .onErrorResume(ex -> {
                        log.warn("Batch text analysis failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.getResults() != null) {
                log.info("Batch text analysis complete for {} proposals", response.getResults().size());
                return response.getResults().stream()
                        .map(TextAnalysisResult::from)
                        .toList();
            }

        } catch (Exception e) {
            log.warn("Batch text analysis error: {}", e.getMessage());
        }

        // Fallback: return defaults for all
        return proposals.stream()
                .map(p -> TextAnalysisResult.defaults())
                .toList();
    }

    /**
     * Simple text relevance analysis between two texts.
     * Used for comparing submission content against requirements or proposals.
     */
    public TextRelevanceResult analyzeTextRelevance(String text1, String text2) {
        if (!mlConfig.isMlEnabled()) {
            return TextRelevanceResult.defaults();
        }

        if (text1 == null || text1.isBlank() || text2 == null || text2.isBlank()) {
            return TextRelevanceResult.defaults();
        }

        try {
            // Use the existing endpoint but treat text1 as proposal and text2 as description
            TextAnalysisRequest request = new TextAnalysisRequest();
            request.setTaskDescription(text2);
            request.setTaskSkills(List.of()); // No specific skills for general relevance
            request.setProposalText(text1);

            var response = mlWebClient
                    .post()
                    .uri("/analyze-text")
                    .body(Mono.just(request), TextAnalysisRequest.class)
                    .retrieve()
                    .bodyToMono(TextAnalysisResponse.class)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .block();

            if (response != null && response.getProposalRelevanceScore() != null) {
                TextRelevanceResult result = new TextRelevanceResult();
                result.setSimilarityScore(response.getProposalRelevanceScore());
                result.setKeywordOverlap(response.getKeywordCoverageScore() != null ? 
                        response.getKeywordCoverageScore() : 0.5);
                return result;
            }

        } catch (Exception e) {
            log.warn("Text relevance analysis failed: {}", e.getMessage());
        }

        return TextRelevanceResult.defaults();
    }

    // ================================
    // Request/Response DTOs
    // ================================

    @Data
    public static class TextAnalysisRequest {
        private String taskDescription;
        private List<String> taskSkills;
        private String proposalText;
    }

    @Data
    public static class TextAnalysisResponse {
        private Double proposalRelevanceScore;
        private Double keywordCoverageScore;
        private Double combinedTextScore;
    }

    @Data
    public static class BatchTextAnalysisRequest {
        private String taskDescription;
        private List<String> taskSkills;
        private List<String> proposals;
    }

    @Data
    public static class BatchTextAnalysisResponse {
        private List<TextAnalysisResponse> results;
    }

    /**
     * Result wrapper with convenient defaults
     */
    @Data
    public static class TextAnalysisResult {
        private double proposalRelevanceScore;
        private double keywordCoverageScore;
        private double combinedTextScore;

        public static TextAnalysisResult defaults() {
            TextAnalysisResult result = new TextAnalysisResult();
            result.setProposalRelevanceScore(DEFAULT_RELEVANCE_SCORE);
            result.setKeywordCoverageScore(DEFAULT_COVERAGE_SCORE);
            result.setCombinedTextScore((DEFAULT_RELEVANCE_SCORE + DEFAULT_COVERAGE_SCORE) / 2);
            return result;
        }

        public static TextAnalysisResult from(TextAnalysisResponse response) {
            TextAnalysisResult result = new TextAnalysisResult();
            result.setProposalRelevanceScore(
                    response.getProposalRelevanceScore() != null ? response.getProposalRelevanceScore() : DEFAULT_RELEVANCE_SCORE);
            result.setKeywordCoverageScore(
                    response.getKeywordCoverageScore() != null ? response.getKeywordCoverageScore() : DEFAULT_COVERAGE_SCORE);
            result.setCombinedTextScore(
                    response.getCombinedTextScore() != null ? response.getCombinedTextScore() : 
                            (result.getProposalRelevanceScore() + result.getKeywordCoverageScore()) / 2);
            return result;
        }
    }

    /**
     * Simple text relevance result for comparing two texts
     */
    @Data
    public static class TextRelevanceResult {
        private double similarityScore;
        private double keywordOverlap;

        public static TextRelevanceResult defaults() {
            TextRelevanceResult result = new TextRelevanceResult();
            result.setSimilarityScore(0.5);
            result.setKeywordOverlap(0.5);
            return result;
        }
    }
}
