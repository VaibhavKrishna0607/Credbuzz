package com.credbuzz.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from ML prediction service
 * Received from POST http://localhost:8000/predict
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResponse {
    
    /**
     * Predicted probability of successful task completion (0.0 - 1.0)
     */
    private Double successProbability;

    /**
     * Optional confidence score from ML model
     */
    private Double confidence;

    /**
     * Optional model version for tracking
     */
    private String modelVersion;
}
