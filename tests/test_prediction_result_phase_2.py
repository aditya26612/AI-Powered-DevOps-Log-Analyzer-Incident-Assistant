from app.domain.prediction_result import PredictionResult


def test_prediction_result():

    result = PredictionResult(
        prediction=-1,
        is_anomaly=True,
        decision_score=-0.42,
    )

    assert result.prediction == -1

    assert result.is_anomaly is True

    assert result.decision_score == -0.42