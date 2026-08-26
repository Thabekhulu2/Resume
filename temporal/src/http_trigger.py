from __future__ import annotations
import asyncio
import logging
import uuid
from aiohttp import web
from temporalio.client import Client

from .config import settings
from .workflows.score_resume_fit.workflow import ScoreResumeFitRequest, ScoreResumeFitWorkflow

logger = logging.getLogger(__name__)

REQUIRED_FIELDS = ("resume_storage_path", "job_description_entity_id", "jd_text")


def create_app(client: Client) -> web.Application:
    async def start_scoring(request: web.Request) -> web.Response:
        payload = await request.json()
        missing = [field for field in REQUIRED_FIELDS if not payload.get(field)]
        if missing:
            return web.json_response({"error": f"missing required field(s): {', '.join(missing)}"}, status=400)

        workflow_request = ScoreResumeFitRequest(
            resume_storage_path=payload["resume_storage_path"],
            job_description_entity_id=payload["job_description_entity_id"],
            jd_text=payload["jd_text"],
            candidate_entity_id=payload.get("candidate_entity_id"),
            created_by=payload.get("created_by"),
        )

        handle = await client.start_workflow(
            ScoreResumeFitWorkflow.run,
            workflow_request,
            id=f"score-resume-fit-{uuid.uuid4()}",
            task_queue=settings.temporal_task_queue,
        )
        logger.info("start_scoring", extra={"workflow_id": handle.id})
        return web.json_response({"workflow_id": handle.id})

    app = web.Application()
    app.router.add_post("/start-scoring", start_scoring)
    return app


async def run_http_trigger(client: Client) -> None:
    app = create_app(client)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", settings.http_trigger_port)
    await site.start()
    logger.info("HTTP trigger endpoint started", extra={"port": settings.http_trigger_port})
    await asyncio.Event().wait()
