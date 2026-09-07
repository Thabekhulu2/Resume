# Implementation Plan: Candidate Sign-Up Link on Login Page

**Spec:** [0014-login-candidate-signup-link.md](./0014-login-candidate-signup-link.md)
**Ticket:** Closes #25
**Status:** Approved (Gate 1)

## Phase 1: Frontend

- [x] `frontend/src/routes/login.tsx`: import `Link` from `@tanstack/react-router`, add a centered line below the form inside `CardContent`: "New candidate? " + a `Link to="/candidate/signup"` reading "Sign up"

## Phase 2: Verification

- [x] `tsc --noEmit` — no new errors vs. the existing baseline
- [x] Confirm `/candidate/signup` route exists and is unauthenticated-reachable (already true, unchanged)
- [x] Manual check: link renders, points to the right href, existing sign-in form/error/submit behavior unchanged

## Out of scope

- Recruiter sign-up (spec Non-Goals)
