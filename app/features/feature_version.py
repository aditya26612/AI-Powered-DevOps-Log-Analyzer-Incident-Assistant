"""
===============================================================================
DevInsight AI - Feature Schema Version
===============================================================================

Defines the version of the feature extraction schema.

IMPORTANT:
This version MUST be incremented whenever:

- A feature is added.
- A feature is removed.
- A feature is renamed.
- Feature order changes.
- Feature calculation logic changes.

This version is stored alongside every trained model and verified during
application startup to ensure compatibility between the Feature Engineering
pipeline and the trained ML model.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from typing import Final

# =============================================================================
# Feature Schema Version
# =============================================================================

FEATURE_SCHEMA_VERSION: Final[str] = "1.0.0"