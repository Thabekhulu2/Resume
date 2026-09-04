// Candidate job application flow (spec: docs/specs/0010-candidate-job-application.md)
// Returns a short-lived signed URL for the caller's OWN submitted resume.
// Ownership is resolved server-side via entities.applicant_id -- the caller
// cannot request another candidate's file by any input they control.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const PUBLIC_SUPABASE_URL = Deno.env.get("PUBLIC_SUPABASE_URL") ?? SUPABASE_URL;

interface GetMyResumeUrlRequest {
  job_id?: string;
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

  const authHeader = req.headers.get("Authorization") ?? "";
  const callerClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
  });

  const { data: userData, error: userError } = await callerClient.auth.getUser();
  if (userError || !userData?.user) {
    return jsonResponse({ error: "unauthenticated" }, 401);
  }

  const body = (await req.json().catch(() => ({}))) as GetMyResumeUrlRequest;

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  let versionQuery = supabase
    .from("entity_versions")
    .select("data, entities!inner(applicant_id)")
    .eq("is_current", true)
    .eq("entities.applicant_id", userData.user.id);

  if (body.job_id) {
    versionQuery = versionQuery.eq("data->>applied_to_job_id", body.job_id);
  }

  const { data: version, error: versionError } = await versionQuery.limit(1).maybeSingle();

  if (versionError || !version) {
    return jsonResponse({ error: "no application found for this user" }, 404);
  }

  const resumePath = (version.data as { resume_file_path?: string } | null)?.resume_file_path;
  if (!resumePath) {
    return jsonResponse({ error: "no resume on file" }, 404);
  }

  const { data: signed, error: signError } = await supabase.storage
    .from("resumes")
    .createSignedUrl(resumePath, 300);

  if (signError || !signed) {
    return jsonResponse({ error: `failed to sign url: ${signError?.message}` }, 500);
  }

  // SUPABASE_URL inside the function is the internal gateway address (e.g.
  // http://kong:8000 in local dev) -- Kong doesn't preserve the original
  // Host header to the upstream, so it's not reliably recoverable from the
  // request either. Rewrite the signed URL's origin using the same
  // public-facing base the frontend itself uses (PUBLIC_SUPABASE_URL, the
  // functions-side counterpart to VITE_SUPABASE_URL).
  const signedUrl = new URL(signed.signedUrl);
  const publicBase = new URL(PUBLIC_SUPABASE_URL);
  signedUrl.protocol = publicBase.protocol;
  signedUrl.host = publicBase.host;

  return jsonResponse({ url: signedUrl.toString() });
});
