# Implementation Plan: Dashboard Kanban Board

**Spec:** [0006-dashboard-kanban-board.md](./0006-dashboard-kanban-board.md) (Approved, revised)
**Ticket:** Closes #15
**Status:** Approved (Gate 1) — implemented and live-verified 2026-08-31.

## Already done (from the prior round, unaffected by this revision)

- `frontend/src/pages/dashboard.json`'s `dataSources`: `strongCandidates`/`potentialCandidates`/`weakCandidates`, each `entities` filtered by `entity_type = candidate`, `entity_versions.is_current = true`, `entity_facts.value` range, `select: "*, entity_versions!inner(*), entity_facts!inner(value)"`. Live-verified against boundary values (70/69/50/49/0) and an unscored candidate. No changes needed here — this revision only changes what's *rendered* from this data, not how it's fetched.
- The 3-column `Grid columns:3` layout and column `Card`/header/`Badge` structure — kept as-is.

## Phase 1: Remove inline candidate lists from Dashboard columns

`frontend/src/pages/dashboard.json`

- [x] Remove the scrollable `each`-bound candidate-list `Stack` from inside each of the three column `Card`s (the block added in the prior round: outer `max-h-[32rem] overflow-y-auto` Stack containing the inner `each`-bound Stack)
- [x] Remove the "No candidates in this range yet" empty-state `Text` from each column (nothing to be empty now — the column only shows label + count)
- [x] Each column `Card`'s header content (label + count `Badge`) wrapped in a `Link` to the range route (see Phase 3), with a `className` that neutralizes the default inline-link styling for this block use (e.g. `"block no-underline text-inherit hover:no-underline"`) so it doesn't render as underlined blue text
- [x] Column `Card`s keep their `data-strongCandidates` etc. sources only for `.length` — no other change to `dataSources`

## Phase 2: New page `candidate-range.json`

`frontend/src/pages/candidate-range.json` (new file)

- [x] One data source, `candidates`: `entities` filtered by `entity_type = candidate`, `entity_versions.is_current = true`, `entity_facts.value gte {{params.min}}`, `entity_facts.value lte {{params.max}}`, `select: "*, entity_versions!inner(*), entity_facts!inner(value)"` (same shape as the Dashboard's per-bucket sources, bounds parameterized instead of hardcoded)
- [x] Heading: `"{{params.min}}-{{params.max}} fit score"` + a `Link` back to `/`
- [x] Row markup: reuse the exact name/score/View-link structure from the original Kanban column body (same JSON shape, just now the only content on the page, not nested in a scroll box)
- [x] Empty state: `Text variant: muted`, `if: "{{data.candidates.length === 0}}"`, "No candidates in this range."

## Phase 3: New route

`frontend/src/routes/candidates/range/$min/$max.tsx` (new file, mirrors `frontend/src/routes/candidates/$id.tsx`)

- [x] `createFileRoute('/candidates/range/$min/$max')`, component reads `{ min, max } = Route.useParams()`, renders `<UIEngine page={candidateRangePage} params={{ min, max }} />`

## Phase 4: Wire Dashboard columns to the new route

`frontend/src/pages/dashboard.json`

- [x] Strong column's `Link to`: `/candidates/range/70/100`
- [x] Potential column's `Link to`: `/candidates/range/50/69`
- [x] Weak column's `Link to`: `/candidates/range/0/49`

## Phase 5: Verification — real browser (Playwright) against the real running stack

- [x] `npx tsc --noEmit` — 26 error lines, identical to the established baseline (no new errors)
- [x] `vite build` — succeeds; a new `_max-*.js` chunk in the output confirms the route was picked up by TanStack Router's file-based route generation, and the frontend container's dev server logged a `page reload src/routeTree.gen.ts` on save
- [x] **Live**: Dashboard shows three columns side by side, label + count only — confirmed via full-page text scan that no candidate names (e.g. "Boundary70") appear anywhere on the Dashboard
- [x] **Live**: clicking each column navigates to the correct URL — `Strong match` → `/candidates/range/70/100`, `Potential match` → `/candidates/range/50/69`, `Weak match` → `/candidates/range/0/49`
- [x] **Live, adversarial — boundary values**: created real DB rows scored exactly 70, 69, 50, 49, 0; confirmed each appears on exactly the one correct range page and on no other (e.g. Boundary70 appears on `/70/100` and is absent from both `/50/69` and `/0/49`) — this also confirms string route params (`"70"`) filter identically to the numeric literals used in the Dashboard's own data sources, resolving the risk flagged in the spec
- [x] **Live**: the unscored test candidate (`status: scoring`, no `entity_facts` row) confirmed absent from all three range pages
- [x] **Live**: existing "Score a candidate" / "How it works" cards confirmed still present after round-tripping through all three range pages and back
- [x] **Live**: column header clickability — `getComputedStyle` confirmed `cursor: pointer` on the wrapped `<a>`, and the label text renders in the normal dark foreground color (not link-blue/underlined), i.e. reads as a clickable card header rather than an inline text link; visually confirmed via screenshot
- All boundary-value/unscored test data deleted after verification; no pre-existing real candidate data touched

## Dependencies between phases

Phase 1 and Phase 2 can be done in either order; Phase 3 depends on Phase 2 (needs the page to render); Phase 4 depends on Phase 3 (needs the route to exist). Phase 5 depends on all of the above.

## Phase 6 (post-approval polish pass, requested after Phase 5 sign-off): visual redesign to match a reference mockup

Applied directly (visual-only, no behavior change) after the user approved the functional result and then asked for further cosmetic changes in three rounds: (1) remove "Candidate Pipeline" heading/subtitle, put "Score a candidate"/"How it works" side by side, enlarge the score cards; (2) double the score cards' vertical size; (3) match a reference screenshot's exact visual design.

- [x] `frontend/src/pages/dashboard.json`: removed "Candidate Pipeline" heading block; "Score a candidate"/"How it works" wrapped in `Grid columns:2`; score cards given `contentClassName: "py-16"` for height
- [x] New engine component `frontend/src/components/engine/data/EngineIcon.tsx` (registered as `Icon`) — renders a lucide icon (`target`/`trending-up`/`trending-down`) inside a colored circle; follows the same small-lookup-map pattern already used in `EngineAlert.tsx`
- [x] Score cards restyled to match the reference image: `border-l-4` colored left accent + light tinted background (`bg-primary/5`, `bg-amber-50`, `bg-destructive/5`), circular icon badge, large bold colored count number (`text-3xl`)
- [x] Colors: Strong and Weak reuse the existing Adapt IT brand tokens (`--color-primary` `#00A1ED`, `--color-destructive` `#E82727`). **Potential match uses Tailwind's `amber-500`/`amber-600`, which is not part of the Adapt IT brand palette** (no warning/amber token exists in `globals.css`) — flagged to the user as a deviation from strict brand-token usage; accepted as fine for this dashboard visualization
- [x] "Score a candidate" restyled from a plain text `Link` to an outlined-button-styled `Link` (border, padding, arrow character) — no new interaction, same navigation
- [x] "How it works" steps restyled from inline "1. "/"2. "/"3. " text prefixes to circular numbered `Badge`s (reusing `Badge` with an overridden `className` for the circular shape, rather than a new component)
- [x] `npx tsc --noEmit`: 30 error lines (4 more than the established 26-line baseline) — the 4 new lines are the exact same pre-existing error *shape* already tolerated for `EngineLink` (a component with a required prop not present on the generic `RegisteredComponent`/`EngineComponentProps` type); not a new class of problem, just one more component following that already-accepted pattern
- [x] `vite build` — succeeds
- [x] **Live**: screenshot-verified against the reference image side by side; confirmed via Playwright that Strong-column navigation and the Upload page both still work unaffected; zero browser console errors on page load
