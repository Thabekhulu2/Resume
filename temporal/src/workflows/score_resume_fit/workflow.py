from __future__ import annotations
import datetime
from dataclasses import dataclass
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from ...activities import resume_parsing, scoring, supabase_core


@dataclass
class ScoreResumeFitRequest:
    resume_storage_path: str
    job_description_entity_id: str
    jd_text: str
    candidate_entity_id: str | None = None
    created_by: str | None = None


@workflow.defn
class ScoreResumeFitWorkflow:
    @workflow.run
    async def run(self, request: ScoreResumeFitRequest) -> dict:
        candidate_entity_id = request.candidate_entity_id
        try:
            resume_text = await workflow.execute_activity(
                resume_parsing.extract_resume_text,
                request.resume_storage_path,
                start_to_close_timeout=datetime.timedelta(minutes=2),
            )

            score = await workflow.execute_activity(
                scoring.extract_and_score,
                args=[resume_text, request.jd_text],
                start_to_close_timeout=datetime.timedelta(minutes=3),
                retry_policy=RetryPolicy(maximum_attempts=3),
            )

            candidate_data = {
                "name": score.name,
                "resume_file_path": request.resume_storage_path,
                "resume_text": resume_text,
                "extracted": {
                    "skills": score.skills,
                    "experience": score.experience,
                },
                "status": "scored",
            }

            if candidate_entity_id:
                entity_result = await workflow.execute_activity(
                    supabase_core.update_entity_scd2,
                    args=[candidate_entity_id, candidate_data, request.created_by],
                    start_to_close_timeout=datetime.timedelta(seconds=30),
                )
            else:
                entity_result = await workflow.execute_activity(
                    supabase_core.create_entity,
                    args=["candidate", candidate_data, request.created_by],
                    start_to_close_timeout=datetime.timedelta(seconds=30),
                )
                candidate_entity_id = entity_result.entity_id

            await workflow.execute_activity(
                supabase_core.create_relationship,
                args=[request.job_description_entity_id, candidate_entity_id, "candidate_scored_against_job", {}],
                start_to_close_timeout=datetime.timedelta(seconds=30),
            )

            fact_metadata = {"reasoning": score.reasoning, "observed_at": workflow.now().isoformat()}
            fact_result = await workflow.execute_activity(
                supabase_core.upsert_entity_fact,
                args=[candidate_entity_id, "jd_fit_score", score.score, fact_metadata],
                start_to_close_timeout=datetime.timedelta(seconds=30),
            )

            return {
                "candidate_entity_id": candidate_entity_id,
                "version_id": entity_result.version_id,
                "score": score.score,
                "reasoning": score.reasoning,
                "fact_id": fact_result.get("fact_id"),
            }
        except Exception as exc:
            # Record a distinct "failed" state (per spec's acceptance criteria) rather
            # than leaving the candidate stuck looking like scoring is still in progress.
            if candidate_entity_id:
                # Activity/child failures arrive wrapped (e.g. ActivityError wrapping an
                # ApplicationError) - walk to the innermost cause for a useful message.
                root_cause = exc
                while root_cause.__cause__ is not None:
                    root_cause = root_cause.__cause__

                current = await workflow.execute_activity(
                    supabase_core.get_entity,
                    candidate_entity_id,
                    start_to_close_timeout=datetime.timedelta(seconds=30),
                )
                failed_data = {**current.get("data", {}), "status": "failed", "error": str(root_cause)}
                await workflow.execute_activity(
                    supabase_core.update_entity_scd2,
                    args=[candidate_entity_id, failed_data, request.created_by],
                    start_to_close_timeout=datetime.timedelta(seconds=30),
                )
            raise
