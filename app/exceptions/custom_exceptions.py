class PredictionException(Exception):
    """
    Base exception for all prediction-related errors.
    """

    def __init__(self, message: str):
        super().__init__(message)


class ModelNotLoadedException(PredictionException):
    """
    Raised when prediction artifacts cannot be loaded.
    """

    def __init__(
        self,
        message: str = "Prediction model is unavailable.",
    ):
        super().__init__(message)


class FeatureExtractionException(PredictionException):
    """
    Raised when feature extraction fails.
    """

    def __init__(
        self,
        message: str = "Feature extraction failed.",
    ):
        super().__init__(message)


class InvalidPredictionException(PredictionException):
    """
    Raised when prediction cannot be completed.
    """

    def __init__(
        self,
        message: str = "Prediction failed.",
    ):
        super().__init__(message)