"""
CredBuzz ML Prediction Service
FastAPI application for bid success prediction

Run with: uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .models import (
    PredictionRequest, PredictionResponse, HealthResponse,
    TextAnalysisRequest, TextAnalysisResponse,
    BatchTextAnalysisRequest, BatchTextAnalysisResponse
)
from .predictor import predictor, MODEL_VERSION
from .text_analyzer import get_analyzer

app = FastAPI(
    title="CredBuzz ML Service",
    description="Predicts bid success probability for task auctions",
    version="1.0.0"
)

# CORS middleware for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    analyzer = get_analyzer()
    return HealthResponse(
        status="healthy",
        model_loaded=predictor.model_loaded,
        model_version=MODEL_VERSION,
        text_analyzer_ready=analyzer is not None
    )


@app.get("/health", response_model=HealthResponse)
async def health():
    """Alias for health check"""
    return await health_check()


@app.post("/predict", response_model=PredictionResponse)
async def predict(request: PredictionRequest):
    """
    Predict bid success probability
    
    Takes bid features and returns predicted success probability.
    Falls back to heuristic scoring if no trained model is available.
    """
    try:
        # Convert request to dict for predictor
        features = request.model_dump()
        
        # Get prediction
        success_prob, confidence = predictor.predict(features)
        
        return PredictionResponse(
            successProbability=success_prob,
            confidence=confidence,
            modelVersion=MODEL_VERSION
        )
        
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Prediction failed: {str(e)}"
        )


@app.post("/analyze-text", response_model=TextAnalysisResponse)
async def analyze_text(request: TextAnalysisRequest):
    """
    Analyze proposal text against task description.
    
    Returns:
    - proposalRelevanceScore: Semantic similarity (0-1)
    - keywordCoverageScore: Keyword coverage (0-1)  
    - combinedTextScore: Weighted combination (0-1)
    """
    try:
        analyzer = get_analyzer()
        result = analyzer.analyze_proposal(
            task_description=request.taskDescription,
            task_skills=request.taskSkills,
            proposal_text=request.proposalText
        )
        return TextAnalysisResponse(**result)
        
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Text analysis failed: {str(e)}"
        )


@app.post("/analyze-text/batch", response_model=BatchTextAnalysisResponse)
async def analyze_text_batch(request: BatchTextAnalysisRequest):
    """
    Analyze multiple proposals against a single task description.
    More efficient than calling /analyze-text multiple times.
    """
    try:
        analyzer = get_analyzer()
        results = []
        
        for proposal in request.proposals:
            result = analyzer.analyze_proposal(
                task_description=request.taskDescription,
                task_skills=request.taskSkills,
                proposal_text=proposal
            )
            results.append(TextAnalysisResponse(**result))
        
        return BatchTextAnalysisResponse(results=results)
        
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Batch text analysis failed: {str(e)}"
        )


@app.post("/reload-model")
async def reload_model():
    """Reload the ML model from disk"""
    predictor._load_model()
    return {
        "status": "reloaded",
        "model_loaded": predictor.model_loaded
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
