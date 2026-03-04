"""
ML Predictor - Handles model loading and predictions
"""
import os
import numpy as np
import joblib
from pathlib import Path
from typing import Optional, Tuple

MODEL_PATH = Path(__file__).parent.parent / "models" / "bid_success_model.joblib"
SCALER_PATH = Path(__file__).parent.parent / "models" / "feature_scaler.joblib"

# Feature names in order expected by the model (UPDATED for v3)
FEATURE_NAMES = [
    "skillMatchScore",
    "creditFairness",      # Changed from creditDelta
    "deadlineRealism",     # Changed from deadlineDelta
    "completionRate",
    "avgRating",
    "lateRatio",
    "workloadScore",
    "experienceLevel",
    "proposalRelevanceScore",
    "keywordCoverageScore"
]

# Model version
MODEL_VERSION = "2.0.0"


class BidSuccessPredictor:
    """
    Wrapper class for the ML model
    Falls back to heuristic scoring if no trained model exists
    """
    
    def __init__(self):
        self.model = None
        self.scaler = None
        self.model_loaded = False
        self._load_model()
    
    def _load_model(self):
        """Load trained model and scaler if they exist"""
        try:
            if MODEL_PATH.exists() and SCALER_PATH.exists():
                self.model = joblib.load(MODEL_PATH)
                self.scaler = joblib.load(SCALER_PATH)
                self.model_loaded = True
                print(f"✅ Model loaded from {MODEL_PATH}")
            else:
                print("⚠️ No trained model found - using heuristic fallback")
                self.model_loaded = False
        except Exception as e:
            print(f"❌ Error loading model: {e}")
            self.model_loaded = False
    
    def predict(self, features: dict) -> Tuple[float, float]:
        """
        Predict success probability for a bid.
        All features 0-1 normalized (including experienceLevel from backend).
        Returns (success_probability, confidence) with confidence = abs(prob - 0.5) * 2.
        """
        def get(name: str, default: float) -> float:
            v = features.get(name)
            return default if v is None else float(v)

        skill_match = get("skillMatchScore", 0.0)
        credit_fairness = get("creditFairness", None) or get("creditDelta", 0.5)
        deadline_realism = get("deadlineRealism", None) or get("deadlineDelta", 0.5)
        completion_rate = get("completionRate", 0.6)
        avg_rating = get("avgRating", 0.5)
        late_ratio = get("lateRatio", 0.1)
        workload = get("workloadScore", 0.0)
        exp_level = get("experienceLevel", 0.0)
        proposal_rel = get("proposalRelevanceScore", 0.5)
        keyword_cov = get("keywordCoverageScore", 0.5)

        feature_array = np.array([[
            max(0, min(1, skill_match)),
            max(0, min(1, credit_fairness)),
            max(0, min(1, deadline_realism)),
            max(0, min(1, completion_rate)),
            max(0, min(1, avg_rating)),
            max(0, min(1, late_ratio)),
            max(0, min(1, workload)),
            max(0, min(1, exp_level)),
            max(0, min(1, proposal_rel)),
            max(0, min(1, keyword_cov))
        ]])
        
        if self.model_loaded and self.model is not None:
            prob, conf = self._ml_predict(feature_array)
        else:
            prob, conf = self._heuristic_predict(feature_array, features)
        
        return prob, conf
    
    def _ml_predict(self, features: np.ndarray) -> Tuple[float, float]:
        """Use trained model for prediction"""
        try:
            # Scale features
            scaled = self.scaler.transform(features)
            
            # Get probability predictions
            probas = self.model.predict_proba(scaled)
            
            # Success probability (class 1)
            success_prob = float(probas[0][1])
            
            # Confidence = how far from 0.5 (uncertainty)
            confidence = abs(success_prob - 0.5) * 2
            
            return success_prob, confidence
            
        except Exception as e:
            print(f"ML prediction error: {e}")
            return self._heuristic_predict(features, {})
    
    def _heuristic_predict(self, features: np.ndarray, raw_features: dict) -> Tuple[float, float]:
        """
        Heuristic fallback when no trained model available.
        
        All features are 0-1 normalized except experienceLevel.
        """
        f = features[0]  # First (only) sample
        
        # Weights (sum to 1.0)
        weights = {
            "completionRate": 0.18,
            "lateRatio": 0.12,
            "avgRating": 0.10,
            "skillMatchScore": 0.18,
            "proposalRelevanceScore": 0.10,
            "keywordCoverageScore": 0.05,
            "deadlineRealism": 0.08,
            "creditFairness": 0.07,
            "workloadScore": 0.05,
            "experienceLevel": 0.07
        }
        
        # All features are already 0-1 except experience
        skill_match = f[0]
        credit_fairness = f[1]
        deadline_realism = f[2]
        completion_rate = f[3]
        avg_rating = f[4]
        late_ratio = f[5]
        workload_score = f[6]
        experience = f[7]
        proposal_relevance = f[8]
        keyword_coverage = f[9]
        
        # Invert late ratio and workload (lower is better)
        punctuality = 1.0 - late_ratio
        availability = 1.0 - workload_score
        # experience is already 0-1 normalized from backend
        experience_score = experience
        
        # Calculate weighted score
        score = (
            weights["completionRate"] * completion_rate +
            weights["lateRatio"] * punctuality +
            weights["avgRating"] * avg_rating +
            weights["skillMatchScore"] * skill_match +
            weights["proposalRelevanceScore"] * proposal_relevance +
            weights["keywordCoverageScore"] * keyword_coverage +
            weights["deadlineRealism"] * deadline_realism +
            weights["creditFairness"] * credit_fairness +
            weights["workloadScore"] * availability +
            weights["experienceLevel"] * experience_score
        )
        
        # Confidence is lower for heuristic
        return float(score), 0.5


# Singleton instance
predictor = BidSuccessPredictor()
