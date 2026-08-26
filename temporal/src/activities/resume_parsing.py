from __future__ import annotations
import io
import logging
from docx import Document
from pypdf import PdfReader
from temporalio import activity

from ..config import settings
from .supabase_core import _get_client

logger = logging.getLogger(__name__)


@activity.defn
def extract_resume_text(storage_path: str) -> str:
    client = _get_client()
    file_bytes = client.storage.from_(settings.resumes_storage_bucket).download(storage_path)

    lower_path = storage_path.lower()
    if lower_path.endswith(".pdf"):
        reader = PdfReader(io.BytesIO(file_bytes))
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    elif lower_path.endswith(".docx"):
        document = Document(io.BytesIO(file_bytes))
        text = "\n".join(paragraph.text for paragraph in document.paragraphs)
    else:
        raise ValueError(f"Unsupported resume file format for '{storage_path}'; expected .pdf or .docx")

    logger.info("extract_resume_text", extra={"storage_path": storage_path, "chars": len(text)})
    return text
