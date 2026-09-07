# Candidate Sign-Up Link on Login Page Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-09-07
**Last Updated:** 2026-09-07
**Ticket:** Closes #25

## Overview

The unified login page (spec 0011) has no way for a new candidate to discover `/candidate/signup`. Add a plain link/prompt on the login page pointing there.

## Goals

- A "New candidate? Sign up" link on `/login`, below the sign-in form, navigating to `/candidate/signup`.

## Non-Goals

- No recruiter sign-up link — recruiter accounts are provisioned out-of-band via `scripts/create-recruiter.sh` (spec 0008), intentionally not self-service.
- No changes to `/candidate/signup` itself, `signIn`/`signUp` logic, or role resolution.

## User Stories

### As a new candidate, I want to find the sign-up page from the login page

**Acceptance Criteria:**
- [ ] `/login` shows a link to `/candidate/signup`
- [ ] Clicking it navigates to the existing candidate sign-up page, unchanged
- [ ] Existing recruiter/candidate sign-in flow on `/login` is otherwise unchanged

## Technical Design

Pure frontend, one file: `frontend/src/routes/login.tsx`. Add a `Link` (TanStack Router, same pattern used elsewhere, e.g. `application-job.json`'s "Back to Applications") to `/candidate/signup` under the form, inside the existing `Card`.

## Security Considerations

None — purely a navigational link to an existing public route.

## Open Questions

None.
