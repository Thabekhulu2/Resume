from __future__ import annotations
import json

import pytest

from src.activities import scoring
from src.config import settings

VALID_RESPONSE = json.dumps(
    {
        "name": "Jane Doe",
        "skills": ["Python", "SQL"],
        "experience": [
            {"title": "Engineer", "company": "Acme", "duration": "2020-2023", "summary": "Built things"}
        ],
        "score": 82,
        "reasoning": "Strong match on core skills.",
    }
)


def test_extract_and_score_valid_response(monkeypatch):
    monkeypatch.setattr(settings, "use_local_llm", False)
    monkeypatch.setattr(scoring, "_call_anthropic", lambda prompt: VALID_RESPONSE)

    result = scoring.extract_and_score("resume text", "jd text")

    assert result.name == "Jane Doe"
    assert result.score == 82.0
    assert result.skills == ["Python", "SQL"]
    assert result.experience[0]["title"] == "Engineer"
    assert result.reasoning == "Strong match on core skills."


def test_extract_and_score_uses_local_llm_when_configured(monkeypatch):
    monkeypatch.setattr(settings, "use_local_llm", True)
    monkeypatch.setattr(scoring, "_call_local_llm", lambda prompt: VALID_RESPONSE)

    def _fail_if_called(prompt):
        raise AssertionError("should not call Anthropic when USE_LOCAL_LLM is set")

    monkeypatch.setattr(scoring, "_call_anthropic", _fail_if_called)

    result = scoring.extract_and_score("resume text", "jd text")

    assert result.score == 82.0


@pytest.mark.parametrize(
    "raw_response",
    [
        "not json at all",
        json.dumps({"name": "x", "skills": ["Python"], "experience": [], "score": "not a number", "reasoning": "x"}),
        json.dumps({"name": "x", "skills": "not a list", "experience": [], "score": 50, "reasoning": "x"}),
        json.dumps({"name": "x", "skills": [], "experience": ["missing required fields"], "score": 50, "reasoning": "x"}),
        json.dumps({"name": "x", "skills": [], "experience": [], "score": 50}),
        json.dumps({"name": 123, "skills": [], "experience": [], "score": 50, "reasoning": "x"}),
        json.dumps(["not", "an", "object"]),
    ],
)
def test_extract_and_score_rejects_malformed_response(monkeypatch, raw_response):
    monkeypatch.setattr(settings, "use_local_llm", False)
    monkeypatch.setattr(scoring, "_call_anthropic", lambda prompt: raw_response)

    with pytest.raises(ValueError):
        scoring.extract_and_score("resume text", "jd text")
