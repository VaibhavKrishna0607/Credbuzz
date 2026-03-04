"""
CredBuzz Bid Success Model Training v2.0
=========================================

COMPLETELY REWRITTEN to fix:
1. Feature mismatch between training and prediction
2. Over-reliance on credit delta
3. Missing text-based features
4. Poor differentiation between proposals

Features (10 total, matching predictor.py):
1. skillMatchScore       - How well bidder skills match task requirements (0-1)
2. creditDelta          - Difference from budget (normalized)
3. deadlineDelta        - Timeline deviation from request (days)
4. completionRate       - Bidder's historical completion rate (0-1)
5. avgRating            - Bidder's average rating (1-5)
6. lateRatio            - Bidder's late delivery ratio (0-1)
7. workloadScore        - Current workload (0-1, lower = more available)
8. experienceLevel      - Tasks completed count
9. proposalRelevanceScore - Semantic similarity to task (0-1)
10. keywordCoverageScore - Keyword coverage (0-1)

Training Priority (per user requirements):
1. Historical reliability (completionRate, lateRatio, avgRating) - HIGHEST
2. Skill match - HIGH
3. Proposal relevance - MEDIUM-HIGH
4. Timeline realism - MEDIUM
5. Credit delta - LOW (to prevent cheapness bias)
"""

import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, roc_auc_score, accuracy_score
import joblib

# Paths
MODEL_DIR = Path(__file__).parent / "models"
DATA_DIR = Path(__file__).parent / "data"

# All 10 features matching predictor.py
FEATURE_NAMES = [
    "skillMatchScore",
    "creditDelta",
    "deadlineDelta",
    "completionRate",
    "avgRating",
    "lateRatio",
    "workloadScore",
    "experienceLevel",
    "proposalRelevanceScore",
    "keywordCoverageScore"
]


def generate_realistic_training_data(n_samples: int = 5000) -> pd.DataFrame:
    """
    Generate synthetic training data with realistic patterns.
    
    Key insight: Success should correlate STRONGLY with:
    1. High completion rate + low late ratio (reliability)
    2. Skill match (competence)
    3. Proposal relevance (engagement)
    
    Success should correlate WEAKLY with:
    - Credit delta (cheap ≠ good)
    """
    np.random.seed(42)
    
    data = []
    
    for _ in range(n_samples):
        # Generate bidder archetype
        archetype = np.random.choice([
            "reliable_expert",    # High skills, great track record
            "new_promising",      # New but engaged proposals
            "cheap_unreliable",   # Undercuts but poor delivery
            "experienced_mediocre", # Some experience, so-so quality
            "scammer",            # Very cheap, vague proposals
        ], p=[0.25, 0.20, 0.15, 0.25, 0.15])
        
        # Generate features based on archetype
        if archetype == "reliable_expert":
            # Gold-tier bidders: high skills, great history, relevant proposals
            skillMatchScore = np.random.uniform(0.7, 1.0)
            completionRate = np.random.uniform(0.85, 1.0)
            avgRating = np.random.uniform(4.2, 5.0)
            lateRatio = np.random.uniform(0.0, 0.1)
            experienceLevel = np.random.randint(15, 100)
            proposalRelevanceScore = np.random.uniform(0.7, 0.95)
            keywordCoverageScore = np.random.uniform(0.6, 0.9)
            creditDelta = np.random.uniform(-0.1, 0.2)  # Fair pricing
            deadlineDelta = np.random.uniform(-1, 2)  # Realistic timelines
            workloadScore = np.random.uniform(0.1, 0.4)  # Available
            success_prob = 0.92  # Very likely to succeed
            
        elif archetype == "new_promising":
            # New bidders with engaged proposals but no history
            skillMatchScore = np.random.uniform(0.5, 0.85)
            completionRate = np.random.uniform(0.0, 0.5)  # Little history
            avgRating = np.random.uniform(0.0, 4.0)  # Few ratings
            lateRatio = np.random.uniform(0.0, 0.2)  # Little data
            experienceLevel = np.random.randint(0, 5)
            proposalRelevanceScore = np.random.uniform(0.6, 0.9)  # Engaged!
            keywordCoverageScore = np.random.uniform(0.5, 0.85)
            creditDelta = np.random.uniform(-0.1, 0.3)
            deadlineDelta = np.random.uniform(0, 3)
            workloadScore = np.random.uniform(0.0, 0.3)  # Very available
            success_prob = 0.65  # Moderate-good chance
            
        elif archetype == "cheap_unreliable":
            # Undercutters with poor delivery
            skillMatchScore = np.random.uniform(0.3, 0.6)
            completionRate = np.random.uniform(0.4, 0.7)
            avgRating = np.random.uniform(2.5, 3.5)
            lateRatio = np.random.uniform(0.3, 0.7)  # Often late!
            experienceLevel = np.random.randint(3, 20)
            proposalRelevanceScore = np.random.uniform(0.3, 0.6)  # Generic
            keywordCoverageScore = np.random.uniform(0.2, 0.5)
            creditDelta = np.random.uniform(-0.5, -0.2)  # CHEAP!
            deadlineDelta = np.random.uniform(-5, -2)  # Unrealistic promise
            workloadScore = np.random.uniform(0.4, 0.8)
            success_prob = 0.30  # Usually fails
            
        elif archetype == "experienced_mediocre":
            # Average performers
            skillMatchScore = np.random.uniform(0.4, 0.7)
            completionRate = np.random.uniform(0.6, 0.85)
            avgRating = np.random.uniform(3.0, 4.0)
            lateRatio = np.random.uniform(0.15, 0.35)
            experienceLevel = np.random.randint(8, 40)
            proposalRelevanceScore = np.random.uniform(0.4, 0.7)
            keywordCoverageScore = np.random.uniform(0.35, 0.65)
            creditDelta = np.random.uniform(-0.2, 0.15)
            deadlineDelta = np.random.uniform(-1, 4)
            workloadScore = np.random.uniform(0.3, 0.6)
            success_prob = 0.55  # Coin flip
            
        else:  # scammer
            # Obvious low-quality bids
            skillMatchScore = np.random.uniform(0.0, 0.3)
            completionRate = np.random.uniform(0.0, 0.4)
            avgRating = np.random.uniform(1.0, 2.5)
            lateRatio = np.random.uniform(0.5, 1.0)
            experienceLevel = np.random.randint(0, 3)
            proposalRelevanceScore = np.random.uniform(0.1, 0.35)  # Copy-paste
            keywordCoverageScore = np.random.uniform(0.05, 0.3)
            creditDelta = np.random.uniform(-0.7, -0.4)  # TOO cheap
            deadlineDelta = np.random.uniform(-7, -3)  # Impossible timeline
            workloadScore = np.random.uniform(0.6, 1.0)  # Overloaded
            success_prob = 0.10  # Almost never delivers
        
        # Determine success based on probability
        success = np.random.random() < success_prob
        
        # Add noise to make model learn nuance
        noise_factor = 0.05
        row = {
            "skillMatchScore": np.clip(skillMatchScore + np.random.normal(0, noise_factor), 0, 1),
            "creditDelta": creditDelta + np.random.normal(0, 0.05),
            "deadlineDelta": deadlineDelta + np.random.normal(0, 0.5),
            "completionRate": np.clip(completionRate + np.random.normal(0, noise_factor), 0, 1),
            "avgRating": np.clip(avgRating + np.random.normal(0, 0.2), 0, 5),
            "lateRatio": np.clip(lateRatio + np.random.normal(0, noise_factor), 0, 1),
            "workloadScore": np.clip(workloadScore + np.random.normal(0, noise_factor), 0, 1),
            "experienceLevel": max(0, experienceLevel + int(np.random.normal(0, 2))),
            "proposalRelevanceScore": np.clip(proposalRelevanceScore + np.random.normal(0, noise_factor), 0, 1),
            "keywordCoverageScore": np.clip(keywordCoverageScore + np.random.normal(0, noise_factor), 0, 1),
            "success": int(success),
            "archetype": archetype
        }
        data.append(row)
    
    df = pd.DataFrame(data)
    
    print("\n📊 Dataset Statistics:")
    print(f"Total samples: {len(df)}")
    print(f"Success rate: {df['success'].mean():.1%}")
    print(f"\nArchetype distribution:")
    print(df['archetype'].value_counts())
    
    return df


def train_model(df: pd.DataFrame):
    """Train a Gradient Boosting model with proper feature prioritization"""
    
    X = df[FEATURE_NAMES].values
    y = df["success"].values
    
    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    # Scale
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    # Use Gradient Boosting for better probability calibration
    model = GradientBoostingClassifier(
        n_estimators=150,
        max_depth=5,
        learning_rate=0.1,
        min_samples_split=10,
        min_samples_leaf=5,
        subsample=0.8,
        random_state=42
    )
    
    print("\n🚀 Training Gradient Boosting model...")
    model.fit(X_train_scaled, y_train)
    
    # Evaluate
    print("\n" + "="*60)
    print("📈 MODEL EVALUATION")
    print("="*60)
    
    # Cross-validation
    cv_scores = cross_val_score(model, X_train_scaled, y_train, cv=5, scoring='roc_auc')
    print(f"\nCross-validation ROC-AUC: {cv_scores.mean():.3f} (+/- {cv_scores.std()*2:.3f})")
    
    # Test set
    y_pred = model.predict(X_test_scaled)
    y_proba = model.predict_proba(X_test_scaled)[:, 1]
    
    print(f"\nTest Accuracy: {accuracy_score(y_test, y_pred):.3f}")
    print(f"Test ROC-AUC: {roc_auc_score(y_test, y_proba):.3f}")
    
    print("\n📋 Classification Report:")
    print(classification_report(y_test, y_pred, target_names=["Failed", "Successful"]))
    
    # Feature importance - CRITICAL CHECK
    print("\n🎯 FEATURE IMPORTANCE (verify credit is LOW):")
    importance_pairs = sorted(
        zip(FEATURE_NAMES, model.feature_importances_),
        key=lambda x: x[1],
        reverse=True
    )
    for name, importance in importance_pairs:
        bar = "█" * int(importance * 50)
        flag = " ⚠️ TOO HIGH!" if name == "creditDelta" and importance > 0.15 else ""
        print(f"  {name:25s}: {importance:.4f} {bar}{flag}")
    
    # Test with example bids
    print("\n🧪 VALIDATION: Test predictions on example bids...")
    test_cases = [
        {
            "name": "Rob (detailed expert)",
            "features": [0.9, 0.0, 1, 0.95, 4.8, 0.05, 0.2, 30, 0.85, 0.8]
        },
        {
            "name": "Arya (solid generic)",
            "features": [0.6, -0.1, 0, 0.80, 4.0, 0.15, 0.3, 15, 0.55, 0.5]
        },
        {
            "name": "Sansa (cheap scammer)",
            "features": [0.3, -0.5, -3, 0.50, 2.8, 0.40, 0.7, 5, 0.25, 0.2]
        },
    ]
    
    print("\n  Expected ranking: Rob > Arya > Sansa")
    print("\n  Results:")
    for case in test_cases:
        X_case = scaler.transform([case["features"]])
        prob = model.predict_proba(X_case)[0][1]
        print(f"    {case['name']:25s}: {prob:.1%}")
    
    return model, scaler


def validate_prediction_differentiation(model, scaler):
    """Verify model differentiates between good and bad bids"""
    
    # Create contrasting examples
    strong_bid = np.array([[
        0.9,   # skillMatchScore - excellent match
        0.05,  # creditDelta - fair price
        1,     # deadlineDelta - realistic
        0.95,  # completionRate - stellar history
        4.9,   # avgRating - excellent
        0.02,  # lateRatio - almost never late
        0.15,  # workloadScore - available
        50,    # experienceLevel - very experienced
        0.9,   # proposalRelevanceScore - highly relevant
        0.85   # keywordCoverageScore - great coverage
    ]])
    
    weak_bid = np.array([[
        0.2,   # skillMatchScore - poor match
        -0.6,  # creditDelta - way too cheap
        -5,    # deadlineDelta - impossible
        0.3,   # completionRate - poor history
        2.0,   # avgRating - bad reviews
        0.6,   # lateRatio - mostly late
        0.9,   # workloadScore - overloaded
        2,     # experienceLevel - inexperienced
        0.2,   # proposalRelevanceScore - generic
        0.15   # keywordCoverageScore - irrelevant
    ]])
    
    strong_scaled = scaler.transform(strong_bid)
    weak_scaled = scaler.transform(weak_bid)
    
    strong_prob = model.predict_proba(strong_scaled)[0][1]
    weak_prob = model.predict_proba(weak_scaled)[0][1]
    
    print("\n🔬 DIFFERENTIATION TEST:")
    print(f"  Strong bid probability: {strong_prob:.1%}")
    print(f"  Weak bid probability:   {weak_prob:.1%}")
    print(f"  Difference:             {abs(strong_prob - weak_prob):.1%}")
    
    if strong_prob > 0.8 and weak_prob < 0.3:
        print("  ✅ Model differentiates well!")
    else:
        print("  ⚠️ Model needs more training data variation")
    
    return strong_prob - weak_prob > 0.4


def save_model(model, scaler):
    """Save model and scaler"""
    MODEL_DIR.mkdir(exist_ok=True)
    
    model_path = MODEL_DIR / "bid_success_model.joblib"
    scaler_path = MODEL_DIR / "feature_scaler.joblib"
    
    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)
    
    print(f"\n✅ Model saved to {model_path}")
    print(f"✅ Scaler saved to {scaler_path}")


def main():
    print("="*60)
    print("🤖 CredBuzz Bid Success Model Training v2.0")
    print("="*60)
    
    MODEL_DIR.mkdir(exist_ok=True)
    DATA_DIR.mkdir(exist_ok=True)
    
    # Generate realistic training data
    print("\n📦 Generating realistic training data...")
    df = generate_realistic_training_data(n_samples=5000)
    
    # Save for inspection
    csv_path = DATA_DIR / "synthetic_training_data.csv"
    df.to_csv(csv_path, index=False)
    print(f"📁 Saved training data to {csv_path}")
    
    # Train
    model, scaler = train_model(df)
    
    # Validate
    if validate_prediction_differentiation(model, scaler):
        save_model(model, scaler)
        print("\n🎉 Training complete! Model differentiates bids correctly.")
        print("   Restart ML service to load new model.")
    else:
        print("\n⚠️ Model saved but differentiation could be better.")
        save_model(model, scaler)


if __name__ == "__main__":
    main()
