"""
Pydantic models for API request/response
"""
from pydantic import BaseModel
from typing import Optional, List


class PredictionRequest(BaseModel):
    """
    Request model matching MLPredictionRequest.java
    Enhanced with text-based scores
    """
    skillMatchScore: Optional[float] = 0.0
    creditDelta: Optional[float] = 0.0
    deadlineDelta: Optional[float] = 0.0
    completionRate: Optional[float] = 0.0
    avgRating: Optional[float] = 0.0
    lateRatio: Optional[float] = 0.0
    workloadScore: Optional[float] = 0.0
    experienceLevel: Optional[float] = 0.0
    # New text-based features
    proposalRelevanceScore: Optional[float] = 0.5
    keywordCoverageScore: Optional[float] = 0.5


class PredictionResponse(BaseModel):
    """
    Response model matching MLPredictionResponse.java
    """
    successProbability: float
    confidence: float
    modelVersion: str = "1.0.0"


class TextAnalysisRequest(BaseModel):
    """
    Request for analyzing proposal text against task description
    """
    taskDescription: str
    taskSkills: Optional[List[str]] = None
    proposalText: str


class TextAnalysisResponse(BaseModel):
    """
    Response with text analysis scores
    """
    proposalRelevanceScore: float
    keywordCoverageScore: float
    combinedTextScore: float


class BatchTextAnalysisRequest(BaseModel):
    """
    Batch request for analyzing multiple proposals
    """
    taskDescription: str
    taskSkills: Optional[List[str]] = None
    proposals: List[str]


class BatchTextAnalysisResponse(BaseModel):
    """
    Batch response with scores for each proposal
    """
    results: List[TextAnalysisResponse]


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str
    text_analyzer_ready: Optional[bool] = True
