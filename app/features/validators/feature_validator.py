"""
===============================================================================
DevInsight AI - Feature Validator
===============================================================================

Validates the complete feature set produced by the Feature Engineering
pipeline before it is converted into a FeatureVector.

Responsibilities
----------------
- Ensure all expected features exist
- Ensure no unknown features exist
- Ensure feature count is correct
- Ensure feature values have supported types

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.features.feature_names import FEATURE_NAMES


class FeatureValidationError(ValueError):
    """
    Raised when the extracted feature set is invalid.
    """


class FeatureValidator:
    """
    Validates extracted feature dictionaries.
    """

    @staticmethod
    def validate(
        features: dict[str, float | int | bool],
    ) -> None:
        """
        Validate the extracted feature dictionary.

        Raises
        ------
        FeatureValidationError
            If validation fails.
        """

        expected = set(FEATURE_NAMES)
        actual = set(features.keys())

        # ---------------------------------------------------------------------
        # Missing features
        # ---------------------------------------------------------------------

        missing = expected - actual

        if missing:
            raise FeatureValidationError(
                f"Missing features: {sorted(missing)}"
            )

        # ---------------------------------------------------------------------
        # Unknown features
        # ---------------------------------------------------------------------

        unknown = actual - expected

        if unknown:
            raise FeatureValidationError(
                f"Unknown features: {sorted(unknown)}"
            )

        # ---------------------------------------------------------------------
        # Feature count
        # ---------------------------------------------------------------------

        if len(features) != len(FEATURE_NAMES):
            raise FeatureValidationError(
                f"Expected {len(FEATURE_NAMES)} features, "
                f"received {len(features)}."
            )

        # ---------------------------------------------------------------------
        # Value types
        # ---------------------------------------------------------------------

        for name, value in features.items():

            if not isinstance(value, (bool, int, float)):
                raise FeatureValidationError(
                    f"Feature '{name}' has unsupported type "
                    f"{type(value).__name__}."
                )