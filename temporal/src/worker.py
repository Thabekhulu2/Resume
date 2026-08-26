from __future__ import annotations
import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from temporalio.client import Client
from temporalio.worker import Worker

from .config import settings
from .activities import supabase_core, notifications, resume_parsing, scoring
from .http_trigger import run_http_trigger
from .workflows.example.approval_workflow import ApprovalWorkflow
from .workflows.score_resume_fit.workflow import ScoreResumeFitWorkflow

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def main() -> None:
    logger.info("Connecting to Temporal", extra={"address": settings.temporal_address, "namespace": settings.temporal_namespace})
    client = await Client.connect(settings.temporal_address, namespace=settings.temporal_namespace)

    activity_executor = ThreadPoolExecutor(max_workers=20)
    worker = Worker(
        client,
        task_queue=settings.temporal_task_queue,
        workflows=[ApprovalWorkflow, ScoreResumeFitWorkflow],
        activities=[
            supabase_core.create_entity,
            supabase_core.update_entity_scd2,
            supabase_core.get_entity,
            supabase_core.append_event,
            supabase_core.create_relationship,
            supabase_core.upsert_entity_fact,
            resume_parsing.extract_resume_text,
            scoring.extract_and_score,
            notifications.send_email,
            notifications.send_notification,
        ],
        activity_executor=activity_executor,
    )

    logger.info("Worker started", extra={"task_queue": settings.temporal_task_queue})
    await asyncio.gather(worker.run(), run_http_trigger(client))


if __name__ == "__main__":
    asyncio.run(main())
