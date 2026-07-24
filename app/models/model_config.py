"""
===============================================================================
DevInsight AI - Model Configuration
===============================================================================

Central configuration for machine learning models.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from typing import Final

# =============================================================================
# Isolation Forest
# =============================================================================

MODEL_NAME: Final = "IsolationForest"

N_ESTIMATORS: Final = 200

CONTAMINATION: Final = 0.05
# Alternative:
# CONTAMINATION = 0.02

MAX_SAMPLES: Final = "auto"

MAX_FEATURES: Final = 1.0

BOOTSTRAP: Final = False

RANDOM_STATE: Final = 42

N_JOBS: Final = -1

# =============================================================================
# Scaler
# =============================================================================

USE_STANDARD_SCALER: Final = True

# =============================================================================
# Training
# =============================================================================

TRAIN_TEST_SPLIT: Final = 0.20

SHUFFLE_DATA: Final = True

# =============================================================================
# Model Version
# =============================================================================

MODEL_VERSION: Final = "1.0.0"

FEATURE_VERSION: Final = "1.0.0"