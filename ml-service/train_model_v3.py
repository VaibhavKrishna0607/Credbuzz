"""
CredBuzz Bid Success Model Training v3.0 - IMPROVED
====================================================

MAJOR IMPROVEMENTS:
1. Cold-start baseline: new users get completionRate=0.6, lateRatio=0.1
2. Proposal relevance: keyword coverage between task and proposal
3. Skill match: handles missing profile skills
4. Credit fairness: properly normalized (1 - abs(delta)/base)
5. Deadline realism: normalized 0-1 score
6. All features scaled 0-1
7. RandomForestClassifier with proper hyperparameters
8. Better synthetic data generation
9. Predictions are meaningful and separable

Features (10 total):
1. skillMatchScore       - Skill overlap or proposal keyword match (0-1)
2. creditFairness        - 1 - abs(proposed-base)/base (0-1)
3. deadlineRealism       - Realistic timeline score (0-1)
4. completionRate        - Bayesian completion rate (0-1)
5. avgRating             - Normalized rating (0-1)
6. lateRatio             - Late delivery ratio (0-1)
7. workloadScore         - Current workload (0-1)
8. experienceLevel       - Normalized 0-1 (log scale from task count)
9. proposalRelevanceScore - matched_keywords/total_required (0-1)
10. keywordCoverageScore - Keyword coverage (0-1)
"""

import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, roc_auc_score, accuracy_score, confusion_matrix
import joblib

# Paths
MODEL_DIR = Path(__file__).parent / "models"
DATA_DIR = Path(__file__).parent / "data"

# Feature names matching predictor.py
FEATURE_NAMES = [
    "skillMatchScore",
    "creditFairness",  # Changed from creditDelta
    "deadlineRealism",  # Changed from deadlineDelta
    "completionRate",
    "avgRating",
    "lateRatio",
    "workloadScore",
    "experienceLevel",
    "proposalRelevanceScore",
    "keywordCoverageScore"
]

MODEL_VERSION = "2.0.0"


def _normalize_experience(raw_count: int) -> float:
    """Normalize experience level to 0-1 (log scale). Matches backend ML request."""
    return min(1.0, np.log10(raw_count + 1) / 2.0)


def generate_improved_training_data(n_samples: int = 600) -> pd.DataFrame:
    """
    Generate synthetic training data (≥500 rows) with clear correlations:
    - Higher skillMatchScore -> success
    - Higher proposalRelevanceScore -> success
    - High lateRatio -> failure
    - Unrealistic deadlines -> failure
    All features 0-1; experienceLevel stored normalized.
    """
    np.random.seed(42)
    n_samples = max(500, n_samples)
    data = []

    for _ in range(n_samples):
        archetype = np.random.choice([
            "expert_reliable",
            "new_promising",
            "experienced_average",
            "cheap_unreliable",
            "scammer",
        ], p=[0.20, 0.25, 0.30, 0.15, 0.10])

        if archetype == "expert_reliable":
            skillMatchScore = np.random.uniform(0.7, 1.0)
            completionRate = np.random.uniform(0.85, 1.0)
            avgRating = np.random.uniform(0.84, 1.0)
            lateRatio = np.random.uniform(0.0, 0.1)
            experience_raw = np.random.randint(15, 100)
            proposalRelevanceScore = np.random.uniform(0.7, 0.95)
            keywordCoverageScore = np.random.uniform(0.6, 0.9)
            creditFairness = np.random.uniform(0.8, 1.0)
            deadlineRealism = np.random.uniform(0.7, 1.0)
            workloadScore = np.random.uniform(0.1, 0.4)
            success_prob = 0.92

        elif archetype == "new_promising":
            skillMatchScore = np.random.uniform(0.5, 0.85)
            completionRate = 0.6
            avgRating = 0.5
            lateRatio = 0.1
            experience_raw = 0
            proposalRelevanceScore = np.random.uniform(0.6, 0.9)
            keywordCoverageScore = np.random.uniform(0.5, 0.85)
            creditFairness = np.random.uniform(0.7, 0.95)
            deadlineRealism = np.random.uniform(0.6, 0.9)
            workloadScore = np.random.uniform(0.0, 0.3)
            success_prob = 0.70

        elif archetype == "experienced_average":
            skillMatchScore = np.random.uniform(0.4, 0.7)
            completionRate = np.random.uniform(0.6, 0.85)
            avgRating = np.random.uniform(0.6, 0.8)
            lateRatio = np.random.uniform(0.15, 0.35)
            experience_raw = np.random.randint(5, 40)
            proposalRelevanceScore = np.random.uniform(0.4, 0.7)
            keywordCoverageScore = np.random.uniform(0.35, 0.65)
            creditFairness = np.random.uniform(0.6, 0.9)
            deadlineRealism = np.random.uniform(0.5, 0.8)
            workloadScore = np.random.uniform(0.3, 0.6)
            success_prob = 0.55

        elif archetype == "cheap_unreliable":
            skillMatchScore = np.random.uniform(0.3, 0.6)
            completionRate = np.random.uniform(0.4, 0.7)
            avgRating = np.random.uniform(0.5, 0.7)
            lateRatio = np.random.uniform(0.3, 0.7)
            experience_raw = np.random.randint(3, 20)
            proposalRelevanceScore = np.random.uniform(0.3, 0.6)
            keywordCoverageScore = np.random.uniform(0.2, 0.5)
            creditFairness = np.random.uniform(0.3, 0.6)
            deadlineRealism = np.random.uniform(0.2, 0.5)
            workloadScore = np.random.uniform(0.4, 0.8)
            success_prob = 0.30

        else:
            skillMatchScore = np.random.uniform(0.0, 0.3)
            completionRate = np.random.uniform(0.0, 0.4)
            avgRating = np.random.uniform(0.2, 0.5)
            lateRatio = np.random.uniform(0.5, 1.0)
            experience_raw = np.random.randint(0, 3)
            proposalRelevanceScore = np.random.uniform(0.1, 0.35)
            keywordCoverageScore = np.random.uniform(0.05, 0.3)
            creditFairness = np.random.uniform(0.1, 0.4)
            deadlineRealism = np.random.uniform(0.0, 0.3)
            workloadScore = np.random.uniform(0.6, 1.0)
            success_prob = 0.10

        success = np.random.random() < success_prob
        noise = 0.03
        experience_norm = _normalize_experience(max(0, experience_raw + int(np.random.normal(0, 2))))
        row = {
            "skillMatchScore": np.clip(skillMatchScore + np.random.normal(0, noise), 0, 1),
            "creditFairness": np.clip(creditFairness + np.random.normal(0, noise), 0, 1),
            "deadlineRealism": np.clip(deadlineRealism + np.random.normal(0, noise), 0, 1),
            "completionRate": np.clip(completionRate + np.random.normal(0, noise), 0, 1),
            "avgRating": np.clip(avgRating + np.random.normal(0, noise), 0, 1),
            "lateRatio": np.clip(lateRatio + np.random.normal(0, noise), 0, 1),
            "workloadScore": np.clip(workloadScore + np.random.normal(0, noise), 0, 1),
            "experienceLevel": np.clip(experience_norm, 0, 1),
            "proposalRelevanceScore": np.clip(proposalRelevanceScore + np.random.normal(0, noise), 0, 1),
            "keywordCoverageScore": np.clip(keywordCoverageScore + np.random.normal(0, noise), 0, 1),
            "success": int(success),
            "archetype": archetype
        }
        data.append(row)
    
    df = pd.DataFrame(data)
    
    print("\nDataset Statistics:")
    print(f"Total samples: {len(df)}")
    print(f"Success rate: {df['success'].mean():.1%}")
    print(f"\nArchetype distribution:")
    print(df['archetype'].value_counts())
    print(f"\nFeature ranges (all 0-1):")
    for col in FEATURE_NAMES:
        print(f"  {col:25s}: {df[col].min():.3f} - {df[col].max():.3f}")
    
    return df


def train_model(df: pd.DataFrame):
    """Train RandomForestClassifier with proper hyperparameters"""
    
    X = df[FEATURE_NAMES].values
    y = df["success"].values
    
    # Split with stratification
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    # Scale features
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    # Use RandomForestClassifier as specified
    model = RandomForestClassifier(
        n_estimators=300,
        max_depth=None,  # No limit
        min_samples_split=5,
        min_samples_leaf=2,
        max_features='sqrt',
        random_state=42,
        n_jobs=-1
    )
    
    print("\nTraining RandomForestClassifier...")
    model.fit(X_train_scaled, y_train)
    
    # Evaluate
    print("\n" + "="*60)
    print("MODEL EVALUATION")
    print("="*60)
    
    # Cross-validation
    cv_scores = cross_val_score(model, X_train_scaled, y_train, cv=5, scoring='roc_auc')
    print(f"\nCross-validation ROC-AUC: {cv_scores.mean():.3f} (+/- {cv_scores.std()*2:.3f})")
    
    # Test set
    y_pred = model.predict(X_test_scaled)
    y_proba = model.predict_proba(X_test_scaled)[:, 1]
    
    print(f"\nTest Accuracy: {accuracy_score(y_test, y_pred):.3f}")
    print(f"Test ROC-AUC: {roc_auc_score(y_test, y_proba):.3f}")
    
    print("\nClassification Report:")
    print(classification_report(y_test, y_pred, target_names=["Failed", "Successful"]))
    
    print("\nConfusion Matrix:")
    cm = confusion_matrix(y_test, y_pred)
    print(f"  TN: {cm[0,0]:3d}  FP: {cm[0,1]:3d}")
    print(f"  FN: {cm[1,0]:3d}  TP: {cm[1,1]:3d}")
    
    # Feature importance
    print("\nFEATURE IMPORTANCE:")
    importance_pairs = sorted(
        zip(FEATURE_NAMES, model.feature_importances_),
        key=lambda x: x[1],
        reverse=True
    )
    for name, importance in importance_pairs:
        bar = "#" * int(importance * 50)
        print(f"  {name:25s}: {importance:.4f} {bar}")
    
    # Test predictions (all features 0-1 including experienceLevel)
    print("\nVALIDATION: Test predictions on example bids...")
    test_cases = [
        {"name": "Expert (detailed, reliable)", "features": [0.9, 0.95, 0.9, 0.95, 0.96, 0.05, 0.2, _normalize_experience(30), 0.85, 0.8]},
        {"name": "New user (good proposal)", "features": [0.7, 0.85, 0.8, 0.6, 0.5, 0.1, 0.2, 0.0, 0.75, 0.7]},
        {"name": "Average (generic proposal)", "features": [0.5, 0.75, 0.7, 0.75, 0.7, 0.2, 0.4, _normalize_experience(15), 0.5, 0.45]},
        {"name": "Cheap (vague proposal)", "features": [0.4, 0.4, 0.4, 0.6, 0.6, 0.4, 0.6, _normalize_experience(8), 0.35, 0.3]},
        {"name": "Scammer (copy-paste)", "features": [0.2, 0.2, 0.2, 0.3, 0.3, 0.7, 0.8, _normalize_experience(1), 0.2, 0.15]},
    ]
    
    print("\n  Expected ranking: Expert > New > Average > Cheap > Scammer")
    print("\n  Results:")
    for case in test_cases:
        X_case = scaler.transform([case["features"]])
        prob = model.predict_proba(X_case)[0][1]
        conf = abs(prob - 0.5) * 2
        print(f"    {case['name']:30s}: {prob:.1%} (confidence: {conf:.1%})")
    
    return model, scaler


def validate_differentiation(model, scaler):
    """Verify model differentiates between strong and weak proposals"""
    
    # Strong bid: new user with excellent proposal (all features 0-1)
    strong_bid = np.array([[
        0.85, 0.90, 0.85, 0.60, 0.50, 0.10, 0.20, 0.0, 0.85, 0.80
    ]])
    # Weak bid: cheap with vague proposal
    weak_bid = np.array([[
        0.30, 0.30, 0.30, 0.40, 0.40, 0.60, 0.80, _normalize_experience(5), 0.25, 0.20
    ]])
    
    strong_scaled = scaler.transform(strong_bid)
    weak_scaled = scaler.transform(weak_bid)
    
    strong_prob = model.predict_proba(strong_scaled)[0][1]
    weak_prob = model.predict_proba(weak_scaled)[0][1]
    
    print("\nDIFFERENTIATION TEST:")
    print(f"  Strong bid (new user, great proposal): {strong_prob:.1%}")
    print(f"  Weak bid (cheap, vague proposal):      {weak_prob:.1%}")
    print(f"  Difference:                             {abs(strong_prob - weak_prob):.1%}")
    
    if strong_prob > 0.65 and weak_prob < 0.35 and (strong_prob - weak_prob) > 0.3:
        print("  [OK] Model differentiates well!")
        print("  [OK] New users with good proposals are NOT penalized!")
        return True
    else:
        print("  [WARN] Model needs improvement")
        return False


def save_model(model, scaler):
    """Save model and scaler"""
    MODEL_DIR.mkdir(exist_ok=True)
    
    model_path = MODEL_DIR / "bid_success_model.joblib"
    scaler_path = MODEL_DIR / "feature_scaler.joblib"
    
    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)
    
    print(f"\nModel saved to {model_path}")
    print(f"Scaler saved to {scaler_path}")


def main():
    print("="*60)
    print("CredBuzz Bid Success Model Training v3.0 - IMPROVED")
    print("="*60)
    
    MODEL_DIR.mkdir(exist_ok=True)
    DATA_DIR.mkdir(exist_ok=True)
    
    # Generate training data (at least 500 rows per requirement)
    print("\nGenerating improved training data...")
    df = generate_improved_training_data(n_samples=600)
    
    # Save for inspection
    csv_path = DATA_DIR / "training_data_v3.csv"
    df.to_csv(csv_path, index=False)
    print(f"Saved training data to {csv_path}")
    
    # Train
    model, scaler = train_model(df)
    
    # Validate
    if validate_differentiation(model, scaler):
        save_model(model, scaler)
        print("\nTraining complete! Model is ready.")
        print("   Restart ML service to load new model:")
        print("   cd ml-service && uvicorn app.main:app --reload")
    else:
        print("\nModel saved but could be better.")
        save_model(model, scaler)


if __name__ == "__main__":
    main()
