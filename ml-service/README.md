# CredBuzz ML Service

Prediction service for bid success probability in the CredBuzz auction system.

## Quick Start

### 1. Install Dependencies
```bash
cd ml-service
pip install -r requirements.txt
```

### 2. Run the Service (Heuristic Mode)
Without training data, the service uses heuristic scoring:
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

The service is now running at `http://localhost:8000`

### 3. Test the Service
```bash
curl http://localhost:8000/health
```

## Training the ML Model

### 1. Generate Training Data
Complete several auctions in your app, then export:
```bash
curl http://localhost:8080/api/admin/export-auction-dataset > data/auction_dataset.csv
```

### 2. Train the Model
```bash
python train_model.py
```

### 3. Reload the Model
```bash
curl -X POST http://localhost:8000/reload-model
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Health check |
| GET | `/health` | Health check |
| POST | `/predict` | Get bid success prediction |
| POST | `/reload-model` | Reload model from disk |

### Prediction Request
```json
POST /predict
{
    "skillMatchScore": 0.85,
    "creditDelta": -0.2,
    "deadlineDelta": 0.0,
    "completionRate": 0.95,
    "avgRating": 4.5,
    "lateRatio": 0.1,
    "workloadScore": 0.3,
    "experienceLevel": 50
}
```

### Prediction Response
```json
{
    "successProbability": 0.87,
    "confidence": 0.82,
    "modelVersion": "1.0.0"
}
```

## Architecture

```
ml-service/
├── app/
│   ├── __init__.py
│   ├── main.py          # FastAPI application
│   ├── models.py        # Pydantic request/response models
│   └── predictor.py     # ML model wrapper with heuristic fallback
├── models/
│   ├── bid_success_model.joblib   # Trained model (after training)
│   └── feature_scaler.joblib      # Feature scaler (after training)
├── data/
│   └── auction_dataset.csv        # Training data (from backend export)
├── train_model.py       # Model training script
├── requirements.txt
└── README.md
```

## Feature Descriptions

| Feature | Description | Range |
|---------|-------------|-------|
| skillMatchScore | Cosine similarity between bidder skills and task requirements | 0.0 - 1.0 |
| creditDelta | (proposed - original) / original | -1.0 to ∞ |
| deadlineDelta | Proposed days - average days for category | Can be negative/positive |
| completionRate | Bidder's task completion rate | 0.0 - 1.0 |
| avgRating | Bidder's average rating | 0.0 - 5.0 |
| lateRatio | Ratio of late submissions | 0.0 - 1.0 |
| workloadScore | Current task load indicator | 0.0 - 1.0 |
| experienceLevel | Total completed tasks | 0 - ∞ |

## Model Details

- **Algorithm**: Random Forest Classifier
- **Target**: Binary classification (task completed successfully or not)
- **Fallback**: Weighted heuristic scoring when no trained model available

## Enable ML in Backend

Update `application.properties`:
```properties
ml.service.enabled=true
ml.service.url=http://localhost:8000
ml.service.timeout=5000
```
