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

# Feature names in order expected by the model (now includes text features)
FEATURE_NAMES = [
    "skillMatchScore",
    "creditDelta", 
    "deadlineDelta",
    "completionRate",
    "avgRating",
    "lateRatio",
    "workloadScore",
    "experienceLevel",
    # New text-based features
    "proposalRelevanceScore",
    "keywordCoverageScore"
]

# Model version - bumped for new features
MODEL_VERSION = "1.1.0"


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
        Predict success probability for a bid
        
        Returns:
            Tuple of (success_probability, confidence)
        """
        # Extract features in correct order (including new text features)
        feature_array = np.array([[
            features.get("skillMatchScore", 0.0) or 0.0,
            features.get("creditDelta", 0.0) or 0.0,
            features.get("deadlineDelta", 0.0) or 0.0,
            features.get("completionRate", 0.0) or 0.0,
            features.get("avgRating", 0.0) or 0.0,
            features.get("lateRatio", 0.0) or 0.0,
            features.get("workloadScore", 0.0) or 0.0,
            features.get("experienceLevel", 0.0) or 0.0,
            features.get("proposalRelevanceScore", 0.5) or 0.5,
            features.get("keywordCoverageScore", 0.5) or 0.5
        ]])
        
        if self.model_loaded and self.model is not None:
            return self._ml_predict(feature_array)
        else:
            return self._heuristic_predict(feature_array, features)
    
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
        
        REBALANCED WEIGHTS (per user requirements):
        Priority order:
        1. Historical reliability (completionRate, lateRatio, avgRating) - HIGHEST
        2. Skill match - HIGH  
        3. Proposal relevance (NEW) - MEDIUM-HIGH
        4. Timeline realism - MEDIUM
        5. Credit delta - MEDIUM-LOW
        6. Workload risk - LOW
        """
        f = features[0]  # First (only) sample
        
        # REBALANCED weights prioritizing historical reliability
        weights = {
            # Historical reliability - HIGHEST priority (40% total)
            "completionRate": 0.18,        # Track record is critical
            "lateRatio": 0.12,             # Punctuality matters
            "avgRating": 0.10,             # Past ratings
            
            # Skill match - HIGH priority (18%)
            "skillMatchScore": 0.18,
            
            # Proposal relevance - NEW MEDIUM-HIGH priority (15%)
            "proposalRelevanceScore": 0.10,
            "keywordCoverageScore": 0.05,
            
            # Timeline realism - MEDIUM priority (8%)
            "deadlineDelta": 0.08,
            
            # Credit delta - MEDIUM-LOW priority (7%)
            "creditDelta": 0.07,
            
            # Workload risk - LOW priority (5%)
            "workloadScore": 0.05,
            
            # Experience - LOW priority (7%)
            "experienceLevel": 0.07
        }
        
        # Normalize features to 0-1 scale
        skill_match = max(0, min(1, f[0]))  # Already 0-1
        credit_score = max(0, min(1, 1 - abs(f[1])))  # creditDelta: closer to 0 is better
        deadline_score = max(0, min(1, 1 - abs(f[2]) / 10))  # deadlineDelta
        completion_rate = max(0, min(1, f[3]))  # Already 0-1
        avg_rating = max(0, min(1, f[4] / 5))  # Assuming 5-star scale
        late_penalty = max(0, min(1, 1 - f[5]))  # lateRatio: lower is better
        workload_score = max(0, min(1, 1 - f[6]))  # workloadScore: lower is better (more available)
        experience = max(0, min(1, f[7] / 100))  # Normalize experience
        
        # New text features (already 0-1 normalized)
        proposal_relevance = max(0, min(1, f[8])) if len(f) > 8 else 0.5
        keyword_coverage = max(0, min(1, f[9])) if len(f) > 9 else 0.5
        
        # Calculate weighted score
        score = (
            weights["completionRate"] * completion_rate +
            weights["lateRatio"] * late_penalty +
            weights["avgRating"] * avg_rating +
            weights["skillMatchScore"] * skill_match +
            weights["proposalRelevanceScore"] * proposal_relevance +
            weights["keywordCoverageScore"] * keyword_coverage +
            weights["deadlineDelta"] * deadline_score +
            weights["creditDelta"] * credit_score +
            weights["workloadScore"] * workload_score +
            weights["experienceLevel"] * experience
        )
        
        # Confidence is lower for heuristic (0.5 = moderate confidence)
        return float(score), 0.5


# Singleton instance
predictor = BidSuccessPredictor()
