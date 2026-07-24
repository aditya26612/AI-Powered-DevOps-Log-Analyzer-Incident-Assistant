from datetime import datetime

from pydantic import BaseModel


class ErrorResponse(BaseModel):
    """
    Standard API error response returned by the ML service.
    """

    timestamp: datetime
    status: int
    error: str
    message: str
    path: str