from __future__ import annotations
import json
import logging
import urllib.request
from dataclasses import dataclass
from temporalio import activity

from ..config import settings

logger = logging.getLogger(__name__)

SCORING_PROMPT_TEMPLATE = """You are evaluating a candidate's resume against a job description.

Resume:
{resume_text}

Job Description:
{jd_text}

Respond with ONLY a JSON object (no markdown, no commentary) matching this exact shape:
{{"skills": ["string", ...], "experience": [{{"title": "string", "company": "string", "duration": "string", "summary": "string"}}, ...], "score": <number 0-100>, "reasoning": "string"}}
"""

EXPERIENCE_FIELDS = ("title", "company", "duration", "summary")


@dataclass
class ScoreResult:
    skills: list[str]
    experience: list[dict[str, str]]
    score: float
    reasoning: str


def _call_anthropic(prompt: str) -> str:
    import anthropic

    client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
    message = client.messages.create(
        model=settings.anthropic_model,
        max_tokens=2048,
        messages=[{"role": "user", "content": prompt}],
    )
    return "".join(block.text for block in message.content if block.type == "text")


def _call_local_llm(prompt: str) -> str:
    # TEMPORARY LOCAL TEST PATCH — DO NOT COMMIT AS THE PRODUCTION PATH.
    # Substitutes a local Ollama model for Anthropic Claude so plumbing can be
    # verified without a paid API key. Requires USE_LOCAL_LLM=1; must be unset
    # before merge/deploy (per ADR-0001).
    request = urllib.request.Request(
        f"{settings.ollama_base_url}/api/generate",
        data=json.dumps({"model": settings.ollama_model, "prompt": prompt, "stream": False}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["response"]


def _validate_and_parse(raw_response: str) -> ScoreResult:
    try:
        payload = json.loads(raw_response)
    except json.JSONDecodeError as exc:
        raise ValueError(f"LLM response is not valid JSON: {exc}") from exc

    if not isinstance(payload, dict):
        raise ValueError("LLM response JSON must be an object")

    skills = payload.get("skills")
    experience = payload.get("experience")
    score = payload.get("score")
    reasoning = payload.get("reasoning")

    if not isinstance(skills, list) or not all(isinstance(item, str) for item in skills):
        raise ValueError("LLM response 'skills' must be a list of strings")
    if not isinstance(experience, list) or not all(
        isinstance(item, dict) and all(field in item for field in EXPERIENCE_FIELDS) for item in experience
    ):
        raise ValueError(f"LLM response 'experience' must be a list of objects with fields {EXPERIENCE_FIELDS}")
    if not isinstance(score, (int, float)) or isinstance(score, bool):
        raise ValueError("LLM response 'score' must be a number")
    if not isinstance(reasoning, str):
        raise ValueError("LLM response 'reasoning' must be a string")

    return ScoreResult(skills=skills, experience=experience, score=float(score), reasoning=reasoning)


@activity.defn
def extract_and_score(resume_text: str, jd_text: str) -> ScoreResult:
    prompt = SCORING_PROMPT_TEMPLATE.format(resume_text=resume_text, jd_text=jd_text)
    raw_response = _call_local_llm(prompt) if settings.use_local_llm else _call_anthropic(prompt)
    result = _validate_and_parse(raw_response)
    logger.info("extract_and_score", extra={"score": result.score, "skill_count": len(result.skills)})
    return result
