# AgentInsight UI

These instructions apply to all files under `agentinsight-ui/`.

The root `AGENTS.md` remains authoritative. This file adds frontend-specific rules only.

## Source Of Truth

Before implementing frontend behavior, read the relevant specification under `../../application-build-resources/AgentInsight/specs`.

Do not add screens, controls, analytics, API calls, or visual states that are not described by a specification or directly required to satisfy its acceptance criteria.

If the frontend requirement is unclear, stop and request a specification update.

## Stack

Use:

* React
* TypeScript
* Vite
* Recharts
* Existing CSS and UI primitives in `src/components/ui`

Do not introduce a new UI framework, state library, router, chart library, or styling system unless the specification requires it.

## UI Resources

Frontend resources live in the sibling resource directory:

```text
../../application-build-resources/AgentInsight/template
../../application-build-resources/AgentInsight/ui structure
```

Use those only as references when a specification, task, or user request explicitly calls for them.

Use `template` as the UI component, styling, and template reference.

Use `ui structure` as the React application organization reference.

These resources do not override:

1. the relevant `../../application-build-resources/AgentInsight/specs` document
2. the root `../AGENTS.md`
3. this `agentinsight-ui/AGENTS.md`
4. the existing AgentInsight frontend structure

AgentInsight does not automatically adopt the full resource project structure just because those resources exist.

Do not copy `template` or `ui structure` wholesale into `agentinsight-ui/`.

When borrowing a pattern from the UI resources:

* adapt it to the existing feature-based structure
* use existing UI primitives when possible
* keep styling consistent with `src/styles.css` and `../../application-build-resources/AgentInsight/specs/006-ui-foundation`
* avoid adding new dependencies unless the specification requires them
* document any intentional structural deviation in the final response

## Feature Structure

Frontend code must remain feature based:

```text
src/features/<feature>/
  api/
  components/
  hooks/
  routes/
```

Use existing shared locations only for true shared infrastructure:

```text
src/app/
src/components/
src/config/
src/hooks/
src/lib/
src/utils/
```

Avoid generic dumping grounds. A component that belongs to one feature stays in that feature.

## API Rules

Feature API clients live in `src/features/<feature>/api`.

Use `src/lib/api.ts` for shared request helpers.

Keep API response types explicit and close to the feature that consumes them.

Do not hide backend failures. Show a user-facing error or unsupported state when data cannot be loaded.

## UI Rules

Follow `../../application-build-resources/AgentInsight/specs/006-ui-foundation` when building or changing UI.

Use existing UI primitives before adding new primitives.

Use `lucide-react` icons for icon buttons and feature affordances when an icon is available.

Pages must show real application UI, not marketing or explanatory landing content.

Analytics pages must handle:

* loading state
* empty state
* error state
* unsupported provider capability state when relevant
* responsive desktop and mobile layouts

Text must not overflow or overlap on supported viewport sizes.

## Performance Rules

Run the frontend build before finishing frontend changes:

```text
npm run build
```

Treat Vite chunk-size warnings as work to fix, not as acceptable noise, unless there is a written reason in the final response.

Prefer route-level or feature-level `React.lazy` splitting for Recharts-heavy or rarely used screens.

Do not load every feature screen into the initial application chunk when lazy loading is practical.

## Generated Files

Do not edit `node_modules/`.

Do not edit `dist/` directly.

`dist/` may change only as generated build output. Do not rely on manual changes there.

## Testing And Validation

For frontend changes, at minimum run:

```text
npm run build
```

When behavior is complex or user-facing, add or update tests if a frontend test harness exists. If no frontend test harness exists, validate with TypeScript build and describe the remaining test gap.

For visual or responsive changes, inspect the rendered app when practical.
