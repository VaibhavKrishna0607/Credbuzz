"""
Pydantic models for API request/response
"""
from pydantic import BaseModel
from typing import Optional, List


class PredictionRequest(BaseModel):
    """
    Request model matching MLPredictionRequest.java.
    All features 0-1 normalized (experienceLevel is normalized in backend).
    """
    skillMatchScore: Optional[float] = 0.0
    creditFairness: Optional[float] = None  # 1 - abs(proposed-base)/base
    creditDelta: Optional[float] = None   # backward compat
    deadlineRealism: Optional[float] = None
    deadlineDelta: Optional[float] = None  # backward compat
    completionRate: Optional[float] = 0.6
    avgRating: Optional[float] = 0.5
    lateRatio: Optional[float] = 0.1
    workloadScore: Optional[float] = 0.0
    experienceLevel: Optional[float] = 0.0  # 0-1 normalized from backend
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
