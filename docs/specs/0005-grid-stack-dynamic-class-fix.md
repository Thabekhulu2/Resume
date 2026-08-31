# Grid/Stack Dynamic Tailwind Class Fix Specification

**Status:** Approved
**Owner:** Ndumiso Mpanza
**Created:** 2026-08-31
**Last Updated:** 2026-08-31
**Ticket:** Closes #14

## Overview

`Grid` and `Stack` build their column/gap/spacing Tailwind classes via template literals (e.g. `` `grid-cols-${columns}` ``), which Tailwind v4's automatic content scanner can't detect statically. Confirmed by building the frontend: `grid-cols-2`, `grid-cols-3`, `gap-1`, `gap-6` are all absent from the compiled CSS. This makes both components silently render incorrectly for most numeric prop values.

## Goals

- `Grid columns={N}` renders as an actual N-column grid for any positive integer N
- `Grid gap={N}` and `Stack spacing={N}` apply the correct gap for any positive integer N
- No dependency on a class string coincidentally existing elsewhere in the codebase
- Existing string-valued `columns`/`gap`/`spacing` props (already handled via inline `style`, e.g. `columns: "1fr 2fr"`) are unaffected

## Non-Goals

- Auditing/fixing individual page JSON files whose layout will visually change now that `Grid` actually works (e.g. Scorecard's `Grid columns:2` will render as 2 columns for the first time) — flagged as a possible follow-up, not addressed here
- A general audit of every other dynamically-constructed Tailwind class in the codebase beyond these two components (out of scope unless another instance is found while touching these files)

## Technical Design

### `Grid.tsx`

Mirror the existing `rows` handling (already correctly uses inline `style.gridTemplateRows` for numeric values) for `columns` and `gap`:
- `columns: number` → `style.gridTemplateColumns = \`repeat(${columns}, minmax(0, 1fr))\`` instead of a `grid-cols-${columns}` class
- `gap: number` → `style.gap = \`${gap * 0.25}rem\`` (matching Tailwind's spacing scale, same conversion already used for `columnGap`/`rowGap`) instead of a `gap-${gap}` class
- `columns: string` / `gap: string` paths are already inline-style-based and unchanged

### `Stack.tsx`

- `spacing: number` → `style.gap = \`${spacing * 0.25}rem\`` instead of a `gap-${spacing}` class (same conversion as above)
- `spacing: string` path already sets `style.gap = spacing` directly and is unchanged

### Security Considerations

None — presentational component internals only.

### Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Pages using `Grid columns:2` (Scorecard, entity-detail) will visually change — from a silent single-column stack to an actual 2-column layout, since the bug is being fixed | Low-medium (visual change, arguably a fix not a regression) | High (certain, by design) | Manually view both affected pages after the fix and confirm the new 2-column layout looks correct, not broken; flag any visual issue found |
| Off-by-factor error in the `* 0.25` rem conversion (Tailwind's default spacing scale is 0.25rem per unit) | Low | Low | Cross-check against Tailwind's default theme scale and the existing `columnGap`/`rowGap` code in the same file, which already uses this exact conversion |

## Testing Strategy

- Build the frontend and confirm `grid-cols`/`gap` classes are no longer needed (the fix works via inline styles, so nothing to grep for — instead, load the built app and visually inspect)
- Live: view the Candidate Scorecard page (uses `Grid columns:2`) before and after, confirm two side-by-side columns
- Live: view the entity-detail page (also uses `Grid columns:2`) and any page using `Stack` with a spacing value that was previously broken (e.g. `spacing: 1`, `spacing: 6`) to confirm gaps now render

## Approval

- [ ] Ndumiso Mpanza
