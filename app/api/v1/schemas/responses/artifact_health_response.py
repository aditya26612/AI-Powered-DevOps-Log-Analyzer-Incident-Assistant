"""
===============================================================================
DevInsight AI - Artifact Health Response
===============================================================================
"""

from pydantic import BaseModel


class ArtifactHealthResponse(BaseModel):
    model: bool
    scaler: bool
    metadata: bool