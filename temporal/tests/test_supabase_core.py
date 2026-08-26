from __future__ import annotations
from types import SimpleNamespace

from src.activities import supabase_core


class _FakeQuery:
    def __init__(self, data):
        self._data = data

    def __getattr__(self, name):
        def chain(*args, **kwargs):
            return self

        return chain

    def execute(self):
        return SimpleNamespace(data=self._data)


class _FakeClient:
    """Queues one `.data` response per `.table(name)` call, in call order."""

    def __init__(self, table_responses):
        self._queues = {name: list(responses) for name, responses in table_responses.items()}

    def table(self, name):
        queue = self._queues.get(name)
        if not queue:
            raise AssertionError(f"Unexpected .table('{name}') call with no queued response left")
        return _FakeQuery(queue.pop(0))


def test_create_entity(monkeypatch):
    client = _FakeClient(
        {
            "entities": [[{"id": "entity-1"}]],
            "entity_versions": [[{"id": "version-1"}]],
        }
    )
    monkeypatch.setattr(supabase_core, "_get_client", lambda: client)

    result = supabase_core.create_entity("candidate", {"resume_text": "..."})

    assert result.entity_id == "entity-1"
    assert result.version_id == "version-1"


def test_update_entity_scd2(monkeypatch):
    client = _FakeClient(
        {
            "entity_versions": [{"version_number": 1}, [{"id": "version-2"}]],
        }
    )
    monkeypatch.setattr(supabase_core, "_get_client", lambda: client)

    result = supabase_core.update_entity_scd2("entity-1", {"status": "scored"})

    assert result.entity_id == "entity-1"
    assert result.version_id == "version-2"


def test_get_entity(monkeypatch):
    client = _FakeClient(
        {
            "entities": [{"id": "entity-1", "entity_type": "candidate"}],
            "entity_versions": [{"id": "version-1", "data": {"status": "scored"}, "version_number": 2}],
        }
    )
    monkeypatch.setattr(supabase_core, "_get_client", lambda: client)

    result = supabase_core.get_entity("entity-1")

    assert result["id"] == "entity-1"
    assert result["data"] == {"status": "scored"}
    assert result["version_number"] == 2


def test_create_relationship(monkeypatch):
    client = _FakeClient({"relationships_v2": [[{"id": "rel-1"}]]})
    monkeypatch.setattr(supabase_core, "_get_client", lambda: client)

    result = supabase_core.create_relationship("job-1", "candidate-1", "candidate_scored_against_job")

    assert result == {"relationship_id": "rel-1", "success": True}


def test_upsert_entity_fact(monkeypatch):
    client = _FakeClient(
        {
            "fact_types": [{"id": "fact-type-1"}],
            "entity_facts": [[{"id": "fact-1"}]],
        }
    )
    monkeypatch.setattr(supabase_core, "_get_client", lambda: client)

    result = supabase_core.upsert_entity_fact("candidate-1", "jd_fit_score", 82.5, {"reasoning": "great fit"})

    assert result == {"fact_id": "fact-1", "success": True}


def test_append_event_stub_returns_true():
    assert supabase_core.append_event("entity-1", "candidate", "created", {}) is True
