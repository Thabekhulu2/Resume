from __future__ import annotations
import asyncio
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from temporalio import activity
from temporalio.testing import WorkflowEnvironment
from temporalio.worker import Worker

from src.activities.supabase_core import EntityResult
from src.activities.scoring import ScoreResult
from src.workflows.score_resume_fit.workflow import ScoreResumeFitRequest, ScoreResumeFitWorkflow


@activity.defn(name="extract_resume_text")
def fake_extract_resume_text(storage_path: str) -> str:
    return "Experienced engineer with Python and SQL skills."


@activity.defn(name="extract_and_score")
def fake_extract_and_score(resume_text: str, jd_text: str) -> ScoreResult:
    return ScoreResult(
        name="Jane Doe",
        skills=["Python", "SQL"],
        experience=[{"title": "Engineer", "company": "Acme", "duration": "2020-2023", "summary": "Built things"}],
        score=88.0,
        reasoning="Strong alignment with required skills.",
    )


@activity.defn(name="extract_and_score")
def failing_extract_and_score(resume_text: str, jd_text: str) -> ScoreResult:
    raise ValueError("malformed LLM response")


@activity.defn(name="update_entity_scd2")
def fake_update_entity_scd2(entity_id: str, attributes, updated_by=None) -> EntityResult:
    return EntityResult(entity_id=entity_id, version_id="version-2")


@activity.defn(name="create_relationship")
def fake_create_relationship(from_entity_id, to_entity_id, relationship_type, attributes=None):
    return {"relationship_id": "rel-1", "success": True}


@activity.defn(name="upsert_entity_fact")
def fake_upsert_entity_fact(entity_id, fact_type_key, value, metadata=None):
    return {"fact_id": "fact-1", "success": True}


@activity.defn(name="get_entity")
def fake_get_entity(entity_id: str):
    return {"id": entity_id, "data": {"resume_file_path": "candidate-1/resume.pdf", "status": "scoring"}}


def _request() -> ScoreResumeFitRequest:
    return ScoreResumeFitRequest(
        resume_storage_path="candidate-1/resume.pdf",
        job_description_entity_id="job-1",
        jd_text="Looking for a Python engineer.",
        candidate_entity_id="candidate-1",
    )


async def _run(activities, request) -> dict:
    async with await WorkflowEnvironment.start_time_skipping() as env:
        task_queue = f"test-queue-{uuid.uuid4()}"
        async with Worker(
            env.client,
            task_queue=task_queue,
            workflows=[ScoreResumeFitWorkflow],
            activities=activities,
            activity_executor=ThreadPoolExecutor(max_workers=5),
            max_concurrent_activities=5,
        ):
            return await env.client.execute_workflow(
                ScoreResumeFitWorkflow.run,
                request,
                id=f"test-score-resume-fit-{uuid.uuid4()}",
                task_queue=task_queue,
            )


def test_score_resume_fit_workflow_happy_path():
    result = asyncio.run(
        _run(
            [
                fake_extract_resume_text,
                fake_extract_and_score,
                fake_update_entity_scd2,
                fake_create_relationship,
                fake_upsert_entity_fact,
            ],
            _request(),
        )
    )

    assert result["candidate_entity_id"] == "candidate-1"
    assert result["version_id"] == "version-2"
    assert result["score"] == 88.0
    assert result["reasoning"] == "Strong alignment with required skills."
    assert result["fact_id"] == "fact-1"


def test_score_resume_fit_workflow_records_failure_status():
    captured = []

    @activity.defn(name="update_entity_scd2")
    def capturing_update_entity_scd2(entity_id, attributes, updated_by=None) -> EntityResult:
        captured.append(attributes)
        return EntityResult(entity_id=entity_id, version_id="version-failed")

    with pytest.raises(Exception):
        asyncio.run(
            _run(
                [
                    fake_extract_resume_text,
                    failing_extract_and_score,
                    fake_get_entity,
                    capturing_update_entity_scd2,
                    fake_create_relationship,
                    fake_upsert_entity_fact,
                ],
                _request(),
            )
        )

    assert captured, "expected the workflow to write a failed status via update_entity_scd2"
    assert captured[-1]["status"] == "failed"
    assert "malformed LLM response" in captured[-1]["error"]
    assert captured[-1]["resume_file_path"] == "candidate-1/resume.pdf"
