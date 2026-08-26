from __future__ import annotations
import logging
from dataclasses import dataclass
from typing import Any, Dict
from temporalio import activity
from supabase import create_client, Client

from ..config import settings

logger = logging.getLogger(__name__)

_client: Client | None = None


def _get_client() -> Client:
    global _client
    if _client is None:
        _client = create_client(settings.supabase_url, settings.supabase_service_role_key)
    return _client


@dataclass
class EntityResult:
    entity_id: str
    version_id: str
    success: bool = True
    error: str | None = None


@activity.defn
def create_entity(entity_type: str, attributes: Dict[str, Any], created_by: str | None = None) -> EntityResult:
    client = _get_client()
    entity = client.table("entities").insert({"entity_type": entity_type}).execute().data[0]
    version = client.table("entity_versions").insert({
        "entity_id": entity["id"],
        "version_number": 1,
        "data": attributes,
    }).execute().data[0]
    logger.info("create_entity", extra={"entity_type": entity_type, "entity_id": entity["id"], "created_by": created_by})
    return EntityResult(entity_id=entity["id"], version_id=version["id"])


@activity.defn
def update_entity_scd2(entity_id: str, attributes: Dict[str, Any], updated_by: str | None = None) -> EntityResult:
    client = _get_client()
    current = (
        client.table("entity_versions")
        .select("version_number")
        .eq("entity_id", entity_id)
        .eq("is_current", True)
        .single()
        .execute()
        .data
    )
    version = client.table("entity_versions").insert({
        "entity_id": entity_id,
        "version_number": current["version_number"] + 1,
        "data": attributes,
    }).execute().data[0]
    logger.info("update_entity_scd2", extra={"entity_id": entity_id, "updated_by": updated_by})
    return EntityResult(entity_id=entity_id, version_id=version["id"])


@activity.defn
def get_entity(entity_id: str) -> Dict[str, Any]:
    client = _get_client()
    entity = client.table("entities").select("*").eq("id", entity_id).single().execute().data
    version = (
        client.table("entity_versions")
        .select("*")
        .eq("entity_id", entity_id)
        .eq("is_current", True)
        .single()
        .execute()
        .data
    )
    return {
        **entity,
        "data": version["data"],
        "version_id": version["id"],
        "version_number": version["version_number"],
    }


@activity.defn
def append_event(entity_id: str, entity_type: str, event_type: str, event_data: Dict[str, Any], actor_id: str | None = None, correlation_id: str | None = None) -> bool:
    logger.info(
        "[STUB] append_event",
        extra={"entity_id": entity_id, "event_type": event_type, "actor_id": actor_id, "correlation_id": correlation_id},
    )
    return True


@activity.defn
def create_relationship(from_entity_id: str, to_entity_id: str, relationship_type: str, attributes: Dict[str, Any] | None = None) -> Dict[str, Any]:
    client = _get_client()
    relationship = client.table("relationships_v2").insert({
        "parent_id": from_entity_id,
        "child_id": to_entity_id,
        "relationship_type": relationship_type,
        "metadata": attributes or {},
    }).execute().data[0]
    logger.info(
        "create_relationship",
        extra={"from": from_entity_id, "to": to_entity_id, "relationship_type": relationship_type},
    )
    return {"relationship_id": relationship["id"], "success": True}


@activity.defn
def upsert_entity_fact(entity_id: str, fact_type_key: str, value: float, metadata: Dict[str, Any] | None = None) -> Dict[str, Any]:
    client = _get_client()
    fact_type = client.table("fact_types").select("id").eq("key", fact_type_key).single().execute().data
    fact = client.table("entity_facts").upsert(
        {
            "entity_id": entity_id,
            "fact_type_id": fact_type["id"],
            "value": value,
            "metadata": metadata or {},
        },
        on_conflict="entity_id,fact_type_id,dimension_id",
    ).execute().data[0]
    logger.info("upsert_entity_fact", extra={"entity_id": entity_id, "fact_type": fact_type_key, "value": value})
    return {"fact_id": fact["id"], "success": True}
