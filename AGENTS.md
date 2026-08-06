# AGENTS

## Collaboration

* Work in small, explicit steps.
* Communicate concisely and for a technical reader.
* State important assumptions before acting.
* If a requirement is ambiguous or has multiple reasonable interpretations, stop and ask for clarification instead of
  choosing silently.
* If new circumstances arise during implementation that affect the implementation process and were not agreed upon
  beforehand, stop the implementation and ask for guidance on the appropriate direction.
* Expand analysis, alternatives, or background only on request, not by default.

## Scope and change discipline

* Prefer the simplest solution that satisfies the request.
* Treat a fix as high quality only if it restores the correct contract and remains robust for every valid event order;
  do not choose a locally minimal workaround that relies on incidental behavior.
* Implement only what was requested.
* Keep changes focused. Do not refactor unrelated code.
* Prefer complete fixes within the requested scope. Report adjacent issues separately.
* Do not add abstractions, configurability, or extension points unless current requirements justify them.
* Push back on solutions that are unnecessarily complex, risky, or inconsistent with the architecture.
* Reuse existing components where possible.
* Create new classes, methods, or fields only when they are architecturally justified. Avoid unnecessary state in
  classes.
* Do not preserve backwards compatibility unless explicitly requested.

## Workflow

* Wait for approval before making file changes that change a tested contract, public API, user-visible behavior,
  persistence format, dependency wiring, or module architecture.
* Do not wait for approval for small, local changes that preserve existing contracts and behavior, such as comments,
  non-behavioral cleanups, mechanical refactors, or straightforward bug fixes fully contained within the requested
  scope.
* If the plan receives comments, update the analysis and plan before proceeding.
* When fixing a bug, understand the root cause first. If the root cause is unclear - propose adding diagnostics logs.
* For bug fixes, add or update a test that reproduces the failing scenario before implementing the fix.
* If a change requires updating a tested contract or changing behavior, describe the exact contract change and ask for
  confirmation before proceeding.
* If a discovered issue is outside the current scope, do not silently fix it. Mention it in the summary with enough
  detail to reproduce or address it later.
* After edits, run the smallest relevant validation and `git diff --check`.

## Architecture and code

* The project is written in Kotlin and uses Gradle Kotlin DSL.
* Follow commit, PR, and branch naming rules from `docs/architecture/git-conventions.md`; when asked about the commit
  name, always follow the rules in that regard.
* Use 4 spaces for indentation.
* Keep imports explicit.
* Match the existing project style and architecture.
* Apply clean code principles, SOLID, dependency injection, and clear separation of concerns.
* Consider performance when designing a solution and mention relevant trade-offs in the analysis.
* When replacing an old API, move directly to the new version instead of leaving deprecated wrappers or duplicated
  APIs.
* Avoid unnecessary state in classes - it is hard to reason about complicated state, so prefer a dry design.
* Do not put separate domain classes / objects into single file - split them into the files named after
  the classes/objects.
* Never break architecture rules and clean code rules, when introducing fixes.

## Domain-specific rules


## Documentation

* Keep documentation short, concrete, and normative.
* Describe the intended implementation and structure.
* Prefer positive instructions over lists of forbidden approaches.
* Avoid documenting obsolete alternatives unless they are necessary for migration or historical context.

## Local environment
