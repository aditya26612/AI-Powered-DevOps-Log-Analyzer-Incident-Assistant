"""
===============================================================================
DevInsight AI - Feature Vector Domain Model
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True, frozen=True)
class FeatureVector:
    """
    Represents the ordered numerical feature vector
    that will be consumed by the ML model.
    """

    values: list[float]
    feature_names: list[str]