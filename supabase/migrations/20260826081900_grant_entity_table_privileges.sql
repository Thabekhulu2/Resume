-- Fixes a base-scaffold gap: the core entity/analytics migrations granted
-- TRUNCATE/REFERENCES/TRIGGER to anon/authenticated/service_role but never the
-- actual DML privileges, so every PostgREST-routed write (Edge Functions using
-- the service_role key, the Temporal worker's supabase-py client) fails with
-- "permission denied for table X" even though RLS is disabled on these tables.
-- No auth/multi-tenancy for v1 (per spec's Non-Goals), so this intentionally
-- grants broadly rather than adding RLS policies.

grant select, insert, update, delete on
  public.entities,
  public.entity_versions,
  public.relationships_v2,
  public.fact_types,
  public.entity_facts,
  public.time_series_points
to anon, authenticated, service_role;

alter default privileges in schema public
  grant select, insert, update, delete on tables to anon, authenticated, service_role;
