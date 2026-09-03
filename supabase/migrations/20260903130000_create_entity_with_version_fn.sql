-- Adds create_entity_with_version(), a shared RPC for atomically creating an
-- entity + its first version. frontend/src/pages/entity-list.json already
-- calls an RPC of this exact name for its "New Entity" button, but it was
-- never created in a migration -- that button has been silently broken
-- since the template was scaffolded. This ticket's Jobs page (spec 0009)
-- needs the same capability, so this fixes both.
--
-- Runs as the caller (no security definer) -- ticket #18's RLS policies
-- already grant authenticated recruiters insert on entities/entity_versions,
-- so no elevated privileges are needed here.

create or replace function create_entity_with_version(p_entity_type text, p_data jsonb)
returns uuid
language plpgsql
as $$
declare
  v_entity_id uuid;
begin
  insert into entities (entity_type)
  values (p_entity_type)
  returning id into v_entity_id;

  insert into entity_versions (entity_id, version_number, data, is_current)
  values (v_entity_id, 1, p_data, true);

  return v_entity_id;
end;
$$;

grant execute on function create_entity_with_version(text, jsonb) to authenticated;
