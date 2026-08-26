// Resume -> candidate profile feature (spec: docs/specs/0001-resume-candidate-profile.md)
// Accepts { candidate_entity_id?, resume_storage_path, job_description_entity_id?, jd_text?, created_by? },
// resolves/creates the job_description entity when only jd_text is given, then triggers
// ScoreResumeFitWorkflow via the Temporal worker's HTTP trigger (temporal/src/http_trigger.py).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SCORING_TRIGGER_URL = Deno.env.get("SCORING_TRIGGER_URL") ?? "http://host.docker.internal:8001";

interface StartScoringRequest {
  candidate_entity_id?: string;
  resume_storage_path?: string;
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

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "method not allowed" }, 405);
  }

  const body = (await req.json()) as StartScoringRequest;

  if (!body.resume_storage_path) {
    return jsonResponse({ error: "resume_storage_path is required" }, 400);
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

  // Ensure the candidate entity exists up front (rather than only inside the
  // workflow) so the caller gets an id back immediately and can navigate to a
  // scorecard page that polls entity_facts while scoring is in progress.
  let candidateEntityId = body.candidate_entity_id;
  if (!candidateEntityId) {
    const { data: entity, error: entityError } = await supabase
      .from("entities")
      .insert({ entity_type: "candidate" })
      .select("id")
      .single();

    if (entityError || !entity) {
      return jsonResponse({ error: `failed to create candidate entity: ${entityError?.message}` }, 500);
    }

    const { error: versionError } = await supabase
      .from("entity_versions")
      .insert({
        entity_id: entity.id,
        version_number: 1,
        data: { resume_file_path: body.resume_storage_path, status: "scoring" },
      });

    if (versionError) {
      return jsonResponse({ error: `failed to create candidate version: ${versionError.message}` }, 500);
    }

    candidateEntityId = entity.id;
  }

  const triggerResponse = await fetch(`${SCORING_TRIGGER_URL}/start-scoring`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      resume_storage_path: body.resume_storage_path,
      job_description_entity_id: jobDescriptionEntityId,
      jd_text: jdText,
      candidate_entity_id: candidateEntityId,
      created_by: body.created_by,
    }),
  });

  const triggerResult = await triggerResponse.json();
  if (!triggerResponse.ok) {
    return jsonResponse({ error: triggerResult.error ?? "failed to start scoring workflow" }, triggerResponse.status);
  }

  return jsonResponse({
    candidate_entity_id: candidateEntityId,
    job_description_entity_id: jobDescriptionEntityId,
    ...triggerResult,
  });
});
