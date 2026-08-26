from __future__ import annotations
import io

import pytest
from docx import Document

from src.activities import resume_parsing


def _build_pdf(text: bytes) -> bytes:
    """Hand-authored minimal single-page PDF containing a text-showing operator."""
    header = b"%PDF-1.4\n"
    objects = [
        b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
        b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
        b"3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> "
        b"/MediaBox [0 0 200 200] /Contents 5 0 R >>\nendobj\n",
        b"4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
    ]
    stream = b"BT /F1 24 Tf 10 100 Td (%s) Tj ET" % text
    objects.append(b"5 0 obj\n<< /Length %d >>\nstream\n%s\nendstream\nendobj\n" % (len(stream), stream))

    offsets = []
    body = b""
    pos = len(header)
    for obj in objects:
        offsets.append(pos)
        body += obj
        pos += len(obj)

    xref_offset = len(header) + len(body)
    xref = b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    for offset in offsets:
        xref += b"%010d 00000 n \n" % offset
    trailer = b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF" % (len(objects) + 1, xref_offset)

    return header + body + xref + trailer


class _FakeStorageFrom:
    def __init__(self, file_bytes: bytes):
        self._file_bytes = file_bytes

    def download(self, path: str) -> bytes:
        return self._file_bytes


class _FakeStorage:
    def __init__(self, file_bytes: bytes):
        self._file_bytes = file_bytes

    def from_(self, bucket: str):
        return _FakeStorageFrom(self._file_bytes)


class _FakeClient:
    def __init__(self, file_bytes: bytes):
        self.storage = _FakeStorage(file_bytes)


def test_extract_resume_text_pdf(monkeypatch):
    pdf_bytes = _build_pdf(b"Hello Resume")
    monkeypatch.setattr(resume_parsing, "_get_client", lambda: _FakeClient(pdf_bytes))

    result = resume_parsing.extract_resume_text("candidate-1/resume.pdf")

    assert "Hello Resume" in result


def test_extract_resume_text_docx(monkeypatch):
    buffer = io.BytesIO()
    document = Document()
    document.add_paragraph("Hello Resume")
    document.save(buffer)
    monkeypatch.setattr(resume_parsing, "_get_client", lambda: _FakeClient(buffer.getvalue()))

    result = resume_parsing.extract_resume_text("candidate-1/resume.docx")

    assert "Hello Resume" in result


def test_extract_resume_text_unsupported_format(monkeypatch):
    monkeypatch.setattr(resume_parsing, "_get_client", lambda: _FakeClient(b""))

    with pytest.raises(ValueError, match="Unsupported resume file format"):
        resume_parsing.extract_resume_text("candidate-1/resume.txt")
