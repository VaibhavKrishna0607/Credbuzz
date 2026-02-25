"""
Text Analysis Module for Proposal Scoring
==========================================

Provides two key features:
1. proposalRelevanceScore - Semantic similarity between task description and bidder proposal
2. keywordCoverageScore - How many important task keywords appear in the proposal
"""
import re
from typing import List, Tuple, Dict, Optional
from collections import Counter
import math

# Stopwords for keyword extraction
STOPWORDS = {
    'a', 'an', 'the', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for',
    'of', 'with', 'by', 'from', 'as', 'is', 'was', 'are', 'were', 'been',
    'be', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could',
    'should', 'may', 'might', 'must', 'shall', 'can', 'need', 'dare', 'ought',
    'used', 'i', 'you', 'he', 'she', 'it', 'we', 'they', 'what', 'which',
    'who', 'whom', 'this', 'that', 'these', 'those', 'am', 'been', 'being',
    'if', 'then', 'else', 'when', 'up', 'down', 'out', 'off', 'over', 'under',
    'again', 'further', 'once', 'here', 'there', 'all', 'each', 'few', 'more',
    'most', 'other', 'some', 'such', 'no', 'nor', 'not', 'only', 'own', 'same',
    'so', 'than', 'too', 'very', 'just', 'also', 'now', 'into', 'your', 'my',
    'me', 'our', 'us', 'any', 'how', 'about', 'get', 'make', 'use', 'using'
}

# Tech/skill keywords that are especially important
TECH_KEYWORDS = {
    'react', 'angular', 'vue', 'javascript', 'typescript', 'python', 'java',
    'nodejs', 'node', 'express', 'django', 'flask', 'spring', 'boot',
    'mongodb', 'mysql', 'postgresql', 'redis', 'docker', 'kubernetes', 'aws',
    'azure', 'gcp', 'api', 'rest', 'graphql', 'microservices', 'frontend',
    'backend', 'fullstack', 'mobile', 'ios', 'android', 'flutter', 'swift',
    'kotlin', 'css', 'html', 'sass', 'tailwind', 'bootstrap', 'figma',
    'design', 'ui', 'ux', 'database', 'sql', 'nosql', 'testing', 'jest',
    'cypress', 'selenium', 'git', 'ci', 'cd', 'devops', 'agile', 'scrum',
    'machine', 'learning', 'ml', 'ai', 'data', 'analytics', 'tensorflow',
    'pytorch', 'nlp', 'computer', 'vision', 'cloud', 'serverless', 'lambda',
    'authentication', 'security', 'oauth', 'jwt', 'payment', 'stripe',
    'responsive', 'animation', 'performance', 'optimization', 'seo'
}


class TextAnalyzer:
    """
    Analyzes task descriptions and bid proposals for relevance matching.
    Uses TF-IDF based similarity (no heavy ML dependencies).
    """
    
    def __init__(self):
        # Could load sentence-transformers here for better embeddings
        # For now, using TF-IDF approach which works well for keyword-rich texts
        self.use_embeddings = False  # Set True if sentence-transformers installed
        self.embedding_model = None
        
        try:
            from sentence_transformers import SentenceTransformer
            self.embedding_model = SentenceTransformer('all-MiniLM-L6-v2')
            self.use_embeddings = True
            print("✅ Loaded sentence-transformers for semantic similarity")
        except ImportError:
            print("⚠️ sentence-transformers not installed, using TF-IDF fallback")
    
    def tokenize(self, text: str) -> List[str]:
        """Clean and tokenize text into words"""
        if not text:
            return []
        
        # Lowercase and extract words
        text = text.lower()
        words = re.findall(r'\b[a-z][a-z0-9+#]*\b', text)
        
        # Remove stopwords but keep tech terms
        return [w for w in words if w not in STOPWORDS or w in TECH_KEYWORDS]
    
    def extract_keywords(self, text: str, top_n: int = 20) -> List[Tuple[str, float]]:
        """
        Extract important keywords from text with TF-IDF inspired scoring.
        Returns list of (keyword, importance_score) tuples.
        """
        tokens = self.tokenize(text)
        if not tokens:
            return []
        
        # Count frequencies
        freq = Counter(tokens)
        total = len(tokens)
        
        # Score keywords: frequency * boost for tech terms
        scored = []
        for word, count in freq.items():
            tf = count / total
            # Boost tech keywords 2x
            boost = 2.0 if word in TECH_KEYWORDS else 1.0
            # Boost longer words slightly (more specific)
            length_boost = 1.0 + (min(len(word), 10) - 3) * 0.05
            score = tf * boost * length_boost
            scored.append((word, score))
        
        # Sort by score and return top N
        scored.sort(key=lambda x: x[1], reverse=True)
        return scored[:top_n]
    
    def compute_keyword_coverage(
        self, 
        task_description: str, 
        proposal_text: str,
        task_skills: Optional[List[str]] = None
    ) -> float:
        """
        Compute how well the proposal covers task keywords.
        Returns score 0.0 - 1.0
        """
        if not task_description or not proposal_text:
            return 0.5  # Neutral score for missing text
        
        # Extract task keywords
        task_keywords = self.extract_keywords(task_description, top_n=15)
        
        # Add explicit skills as high-priority keywords
        if task_skills:
            for skill in task_skills:
                skill_lower = skill.lower()
                # Add skill with high importance if not already present
                existing = [k for k, _ in task_keywords if k == skill_lower]
                if not existing:
                    task_keywords.append((skill_lower, 0.5))
        
        if not task_keywords:
            return 0.5
        
        # Tokenize proposal
        proposal_tokens = set(self.tokenize(proposal_text))
        
        # Calculate weighted coverage
        total_weight = sum(score for _, score in task_keywords)
        covered_weight = sum(
            score for keyword, score in task_keywords 
            if keyword in proposal_tokens
        )
        
        if total_weight == 0:
            return 0.5
        
        return min(1.0, covered_weight / total_weight)
    
    def compute_semantic_similarity(
        self, 
        task_description: str, 
        proposal_text: str
    ) -> float:
        """
        Compute semantic similarity between task and proposal.
        Uses embeddings if available, falls back to Jaccard similarity.
        Returns score 0.0 - 1.0
        """
        if not task_description or not proposal_text:
            return 0.5
        
        if self.use_embeddings and self.embedding_model:
            return self._embedding_similarity(task_description, proposal_text)
        else:
            return self._jaccard_similarity(task_description, proposal_text)
    
    def _embedding_similarity(self, text1: str, text2: str) -> float:
        """Compute cosine similarity using sentence embeddings"""
        try:
            embeddings = self.embedding_model.encode([text1, text2])
            # Cosine similarity
            dot = sum(a * b for a, b in zip(embeddings[0], embeddings[1]))
            norm1 = math.sqrt(sum(a * a for a in embeddings[0]))
            norm2 = math.sqrt(sum(b * b for b in embeddings[1]))
            
            if norm1 == 0 or norm2 == 0:
                return 0.5
            
            similarity = dot / (norm1 * norm2)
            # Normalize from [-1, 1] to [0, 1]
            return (similarity + 1) / 2
            
        except Exception as e:
            print(f"Embedding error: {e}")
            return self._jaccard_similarity(text1, text2)
    
    def _jaccard_similarity(self, text1: str, text2: str) -> float:
        """Fallback: Jaccard similarity on tokenized text"""
        tokens1 = set(self.tokenize(text1))
        tokens2 = set(self.tokenize(text2))
        
        if not tokens1 or not tokens2:
            return 0.5
        
        intersection = len(tokens1 & tokens2)
        union = len(tokens1 | tokens2)
        
        return intersection / union if union > 0 else 0.5
    
    def analyze_proposal(
        self,
        task_description: str,
        task_skills: Optional[List[str]],
        proposal_text: str
    ) -> Dict[str, float]:
        """
        Full analysis of a proposal against a task.
        
        Returns:
            dict with:
            - proposalRelevanceScore: Semantic similarity (0-1)
            - keywordCoverageScore: Keyword coverage (0-1)
            - combinedTextScore: Weighted combination (0-1)
        """
        relevance = self.compute_semantic_similarity(task_description, proposal_text)
        coverage = self.compute_keyword_coverage(task_description, proposal_text, task_skills)
        
        # Weighted combination (relevance slightly more important)
        combined = 0.6 * relevance + 0.4 * coverage
        
        return {
            "proposalRelevanceScore": round(relevance, 4),
            "keywordCoverageScore": round(coverage, 4),
            "combinedTextScore": round(combined, 4)
        }


# Singleton instance
_analyzer: Optional[TextAnalyzer] = None

def get_analyzer() -> TextAnalyzer:
    """Get or create the singleton TextAnalyzer instance"""
    global _analyzer
    if _analyzer is None:
        _analyzer = TextAnalyzer()
    return _analyzer
