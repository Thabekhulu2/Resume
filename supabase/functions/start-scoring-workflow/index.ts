// Resume -> candidate profile feature (spec: docs/specs/0001-resume-candidate-profile.md)
// Bulk scoring (spec: docs/specs/0002-bulk-candidate-scoring.md) added resume_storage_paths.
// Accepts { candidate_entity_id?, resume_storage_path | resume_storage_paths, job_description_entity_id?, jd_text?, created_by? },
// resolves/creates the job_description entity once when only jd_text is given, then triggers
// ScoreResumeFitWorkflow (once per resume) via the Temporal worker's HTTP trigger (temporal/src/http_trigger.py).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SCORING_TRIGGER_URL = Deno.env.get("SCORING_TRIGGER_URL") ?? "http://host.docker.internal:8001";

interface StartScoringRequest {
  candidate_entity_id?: string;
  resume_storage_path?: string;
  resume_storage_paths?: string[];
  job_description_entity_id?: string;
  jd_text?: string;
  job_title?: string;
  created_by?: string;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// Ensures a candidate entity exists for one resume (rather than only inside the
// workflow) so the caller gets an id back immediately, then triggers scoring for it.
async function scoreOneResume(
  supabase: SupabaseClient,
  resumeStoragePath: string,
  jobDescriptionEntityId: string,
  jdText: string | undefined,
  candidateEntityIdInput: string | undefined,
  createdBy: string | undefined,
): Promise<{ candidate_entity_id: string; workflow_id: string }> {
  let candidateEntityId = candidateEntityIdInput;
  if (!candidateEntityId) {
    const { data: entity, error: entityError } = await supabase
      .from("entities")
      .insert({ entity_type: "candidate" })
      .select("id")
      .single();

    if (entityError || !entity) {
      throw new Error(`failed to create candidate entity for ${resumeStoragePath}: ${entityError?.message}`);
    }

    const { error: versionError } = await supabase
      .from("entity_versions")
      .insert({
        entity_id: entity.id,
        version_number: 1,
        data: { resume_file_path: resumeStoragePath, status: "scoring" },
      });

    if (versionError) {
      throw new Error(`failed to create candidate version for ${resumeStoragePath}: ${versionError.message}`);
    }

    candidateEntityId = entity.id;
  }

  const triggerResponse = await fetch(`${SCORING_TRIGGER_URL}/start-scoring`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      resume_storage_path: resumeStoragePath,
      job_description_entity_id: jobDescriptionEntityId,
      jd_text: jdText,
      candidate_entity_id: candidateEntityId,
      created_by: createdBy,
    }),
  });

  const triggerResult = await triggerResponse.json();
  if (!triggerResponse.ok) {
    throw new Error(`failed to start scoring for ${resumeStoragePath}: ${triggerResult.error ?? "unknown error"}`);
  }

  return { candidate_entity_id: candidateEntityId, workflow_id: triggerResult.workflow_id };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "method not allowed" }, 405);
  }

  const body = (await req.json()) as StartScoringRequest;

  const resumeStoragePaths = body.resume_storage_paths?.length
    ? body.resume_storage_paths
    : body.resume_storage_path
      ? [body.resume_storage_path]
      : [];
  if (resumeStoragePaths.length === 0) {
    return jsonResponse({ error: "resume_storage_path or resume_storage_paths is required" }, 400);
  }
  if (!body.job_description_entity_id && !body.jd_text) {
    return jsonResponse({ error: "one of job_description_entity_id or jd_text is required" }, 400);
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  let jobDescriptionEntityId = body.job_description_entity_id;
  let jdText = body.jd_text;

  if (jobDescriptionEntityId) {
    const { data: version, error } = await supabase
      .from("entity_versions")
      .select("data")
      .eq("entity_id", jobDescriptionEntityId)
      .eq("is_current", true)
      .single();

    if (error || !version) {
      return jsonResponse({ error: `job_description_entity_id not found: ${jobDescriptionEntityId}` }, 404);
    }
    jdText = version.data?.jd_text ?? jdText;
  } else {
    const { data: entity, error: entityError } = await supabase
      .from("entities")
      .insert({ entity_type: "job_description" })
      .select("id")
      .single();

    if (entityError || !entity) {
      return jsonResponse({ error: `failed to create job_description entity: ${entityError?.message}` }, 500);
    }

    const { error: versionError } = await supabase
      .from("entity_versions")
      .insert({ entity_id: entity.id, version_number: 1, data: { title: body.job_title, jd_text: body.jd_text } });

    if (versionError) {
      return jsonResponse({ error: `failed to create job_description version: ${versionError.message}` }, 500);
    }

    jobDescriptionEntityId = entity.id;
  }

  // Candidate creation + workflow trigger happens once per resume. For a
  // single resume, candidate_entity_id (if given) may be reused; a batch
  // always creates a fresh candidate per resume. Sequential, not parallel,
  // to keep Storage/Temporal-trigger load predictable and to stop cleanly
  // (with a clear "which resume failed" error) on the first failure rather
  // than silently dropping a resume from the batch.
  const results: { candidate_entity_id: string; workflow_id: string }[] = [];
  try {
    for (const resumeStoragePath of resumeStoragePaths) {
      const result = await scoreOneResume(
        supabase,
        resumeStoragePath,
        jobDescriptionEntityId,
        jdText,
        resumeStoragePaths.length === 1 ? body.candidate_entity_id : undefined,
        body.created_by,
      );
      results.push(result);
    }
  } catch (error) {
    return jsonResponse({ error: error instanceof Error ? error.message : String(error) }, 500);
  }

  if (body.resume_storage_paths?.length) {
    return jsonResponse({
      job_description_entity_id: jobDescriptionEntityId,
      candidates: results,
    });
  }

  return jsonResponse({
    candidate_entity_id: results[0].candidate_entity_id,
    job_description_entity_id: jobDescriptionEntityId,
    workflow_id: results[0].workflow_id,
  });
});
