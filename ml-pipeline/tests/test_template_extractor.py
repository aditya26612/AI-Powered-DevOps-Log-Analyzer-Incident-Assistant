from app.features.utils.template_extractor import TemplateExtractor


def test_template_extractor_normalizes_dynamic_fields():
    msg = "HTTP GET /api/v1/users/4921 completed in 15ms status=200 for ip 192.168.1.5"
    tmpl = TemplateExtractor.extract_template(msg)

    assert "<IP>" in tmpl
    assert "<NUM>" in tmpl
    assert "192.168.1.5" not in tmpl
    assert "4921" not in tmpl


def test_template_extractor_handles_uuids_and_hex():
    msg = "Transaction 16fbefb2-c71c-4f8a-919e-6e8582900fa2 aborted at pointer 0x7fff8921a"
    tmpl = TemplateExtractor.extract_template(msg)

    assert "<UUID>" in tmpl
    assert "<HEX>" in tmpl
    assert "16fbefb2-c71c-4f8a-919e-6e8582900fa2" not in tmpl


def test_template_extractor_empty_string():
    assert TemplateExtractor.extract_template("") == "<EMPTY>"
    assert TemplateExtractor.extract_template("   ") == "<EMPTY>"
