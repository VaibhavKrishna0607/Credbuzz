"""
Training Script for CredBuzz Bid Success Model

Usage:
1. Export training data from backend: GET /api/admin/export-auction-dataset
2. Save as 'data/auction_dataset.csv'
3. Run: python train_model.py

The script will:
- Load and preprocess the CSV data
- Train a Random Forest classifier
- Evaluate with cross-validation
- Save the model and scaler to 'models/' directory
"""
import os
import pandas as pd
import numpy as np
from pathlib import Path
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    classification_report, 
    confusion_matrix, 
    roc_auc_score,
    accuracy_score
)
import joblib

# Paths
DATA_DIR = Path(__file__).parent / "data"
MODEL_DIR = Path(__file__).parent / "models"
DATA_FILE = DATA_DIR / "auction_dataset.csv"

# Feature columns (matching MLPredictionRequest)
FEATURE_COLS = [
    "skillMatchScore",
    "creditDelta",
    "deadlineDelta",
    "completionRate",
    "avgRating",
    "lateRatio",
    "workloadScore",
]

# Target column - what we're predicting
TARGET_COL = "completedSuccessfully"


def load_data():
    """Load and validate the dataset"""
    if not DATA_FILE.exists():
        raise FileNotFoundError(
            f"Dataset not found at {DATA_FILE}\n"
            "Export it from: GET /api/admin/export-auction-dataset"
        )
    
    df = pd.read_csv(DATA_FILE)
    print(f"Loaded {len(df)} records")
    print(f"Columns: {list(df.columns)}")
    
    return df


def preprocess_data(df: pd.DataFrame):
    """Clean and prepare data for training"""
    
    # Only use completed auctions (where we know the outcome)
    df = df[df["wasSelected"] == True].copy()
    print(f"Records with selected bids: {len(df)}")
    
    # Remove records without completion outcome
    df = df[df[TARGET_COL].notna()]
    print(f"Records with completion data: {len(df)}")
    
    if len(df) == 0:
        raise ValueError("No training data available. Need completed tasks with outcome data.")
    
    # Fill missing values
    for col in FEATURE_COLS:
        if col in df.columns:
            df[col] = df[col].fillna(0)
        else:
            print(f"Warning: Missing column {col}, adding with zeros")
            df[col] = 0
    
    # Check for required columns
    available_features = [col for col in FEATURE_COLS if col in df.columns]
    print(f"Using features: {available_features}")
    
    return df, available_features


def train_model(df: pd.DataFrame, feature_cols: list):
    """Train the model and evaluate"""
    
    X = df[feature_cols].values
    y = df[TARGET_COL].astype(int).values
    
    print(f"\nDataset shape: {X.shape}")
    print(f"Target distribution: {np.bincount(y)}")
    
    # Split data
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    # Scale features
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    # Train model - Random Forest with tuned hyperparameters
    model = RandomForestClassifier(
        n_estimators=100,
        max_depth=10,
        min_samples_split=5,
        min_samples_leaf=2,
        class_weight="balanced",  # Handle imbalanced classes
        random_state=42,
        n_jobs=-1
    )
    
    print("\nTraining Random Forest model...")
    model.fit(X_train_scaled, y_train)
    
    # Evaluate
    print("\n" + "="*50)
    print("MODEL EVALUATION")
    print("="*50)
    
    # Cross-validation
    cv_scores = cross_val_score(model, X_train_scaled, y_train, cv=5, scoring='roc_auc')
    print(f"\nCross-validation ROC-AUC: {cv_scores.mean():.3f} (+/- {cv_scores.std()*2:.3f})")
    
    # Test set evaluation
    y_pred = model.predict(X_test_scaled)
    y_proba = model.predict_proba(X_test_scaled)[:, 1]
    
    print(f"\nTest Accuracy: {accuracy_score(y_test, y_pred):.3f}")
    print(f"Test ROC-AUC: {roc_auc_score(y_test, y_proba):.3f}")
    
    print("\nClassification Report:")
    print(classification_report(y_test, y_pred, target_names=["Failed", "Successful"]))
    
    print("\nConfusion Matrix:")
    print(confusion_matrix(y_test, y_pred))
    
    # Feature importance
    print("\nFeature Importance:")
    for name, importance in sorted(
        zip(feature_cols, model.feature_importances_),
        key=lambda x: x[1],
        reverse=True
    ):
        print(f"  {name}: {importance:.4f}")
    
    return model, scaler


def save_model(model, scaler):
    """Save model and scaler to disk"""
    MODEL_DIR.mkdir(exist_ok=True)
    
    model_path = MODEL_DIR / "bid_success_model.joblib"
    scaler_path = MODEL_DIR / "feature_scaler.joblib"
    
    joblib.dump(model, model_path)
    joblib.dump(scaler, scaler_path)
    
    print(f"\n✅ Model saved to {model_path}")
    print(f"✅ Scaler saved to {scaler_path}")


def main():
    print("="*50)
    print("CredBuzz Bid Success Model Training")
    print("="*50)
    
    # Ensure directories exist
    DATA_DIR.mkdir(exist_ok=True)
    MODEL_DIR.mkdir(exist_ok=True)
    
    try:
        # Load data
        df = load_data()
        
        # Preprocess
        df, feature_cols = preprocess_data(df)
        
        # Train
        model, scaler = train_model(df, feature_cols)
        
        # Save
        save_model(model, scaler)
        
        print("\n✅ Training complete!")
        print("Restart the ML service to load the new model.")
        
    except FileNotFoundError as e:
        print(f"\n❌ {e}")
        print("\nTo get training data:")
        print("1. Run some auctions to completion in your app")
        print("2. Export data: curl http://localhost:8080/api/admin/export-auction-dataset > data/auction_dataset.csv")
        print("3. Run this script again")
        
    except ValueError as e:
        print(f"\n❌ {e}")


if __name__ == "__main__":
    main()
