from app.features.feature_names import FEATURE_NAMES
from app.features.validators.feature_validator import (
    FeatureValidationError,
    FeatureValidator,
)


def build_valid_features():
    return {
        name: 0.0
        for name in FEATURE_NAMES
    }


def test_valid_features():

    features = build_valid_features()

    FeatureValidator.validate(features)


def test_missing_feature():

    features = build_valid_features()

    features.pop(FEATURE_NAMES[0])

    try:
        FeatureValidator.validate(features)
        assert False
    except FeatureValidationError:
        assert True


def test_unknown_feature():

    features = build_valid_features()

    features["unknown_feature"] = 1

    try:
        FeatureValidator.validate(features)
        assert False
    except FeatureValidationError:
        assert True


def test_invalid_type():

    features = build_valid_features()

    features[FEATURE_NAMES[0]] = "invalid"

    try:
        FeatureValidator.validate(features)
        assert False
    except FeatureValidationError:
        assert True