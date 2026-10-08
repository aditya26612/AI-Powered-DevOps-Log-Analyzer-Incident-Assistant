import pytest

from app.features.contracts.extractor import FeatureExtractor


class DummyExtractor(FeatureExtractor):

    def extract(self, log):
        return {"dummy": 1}


def test_dummy_extractor():

    extractor = DummyExtractor()

    result = extractor.extract({})

    assert result["dummy"] == 1


def test_cannot_instantiate_base_class():

    with pytest.raises(TypeError):
        FeatureExtractor()