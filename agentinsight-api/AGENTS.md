# AgentInsight API

These instructions apply to all files under `agentinsight-api/`.

The root `AGENTS.md` remains authoritative. This file adds backend-specific rules only.

## Source Of Truth

Before implementing backend behavior, read the relevant specification under `../../application-build-resources/AgentInsight/specs`.

Implementation must satisfy:

1. specification
2. requirements
3. acceptance criteria
4. tasks
5. test plan

Do not implement backend behavior that is not described by a specification.

If the backend contract or data model is unclear, stop and request a specification update.

## Stack

Use:

* Java 21
* Spring Boot
* Gradle
* SQLite
* jOOQ where persistence logic requires structured database access

Do not introduce a new persistence engine, web framework, serialization stack, or build tool unless the specification requires it.

## Architecture

Follow Clean Architecture:

```text
api/controller
service
repository
infrastructure/source
model
```

Controllers must remain thin:

* request mapping
* validation
* delegation to services
* response return

Business logic belongs in services.

Persistence logic belongs in repositories.

External filesystem, Codex source, provider source, and SQLite integrations belong in infrastructure/source or provider adapter code.

## Java Rules

Use:

* constructor injection
* immutable DTOs
* Java records for request, response, and read-model shapes where appropriate
* clear package ownership by feature

Avoid:

* field injection
* static mutable state
* business logic in controllers
* large cross-feature utility classes
* swallowing errors without at least traceable warning/error behavior when the spec requires it

## Provider And Import Safety

AgentInsight is read-only with respect to provider source files.

Never modify:

```text
~/.codex
history.jsonl
sessions/
state_*.sqlite
```

Provider adapters may read source files only.

Open provider-owned SQLite databases in read-only mode.

Application-owned writes are allowed only under configured AgentInsight data paths, such as `agentinsight.database-path`.

Import failures must not stop the whole import when the specification says to continue. Corrupt files should be logged or surfaced as warnings while processing continues.

Unknown provider events must be ignored safely and traceably.

## API Rules

Backend APIs must match the relevant `api.md` specification.

Use stable response shapes with explicit model records.

Analytics APIs that support provider filtering must accept provider filters without leaking provider-specific parsing logic into analytics services.

Provider-specific parsing belongs in provider adapters.

Analytics modules consume normalized provider-neutral records.

## Persistence Rules

Keep persistence deterministic and traceable.

Do not hardcode pricing, provider paths, or model-specific assumptions in business logic.

Pricing must remain configurable.

When adding tables, ensure migrations or schema creation match the spec data model.

Use structured SQL access instead of ad hoc parsing for database-backed data.

## Testing And Validation

For backend changes, run:

```text
./gradlew test
```

Every specification implementation needs:

* unit tests
* integration tests when persistence, filesystem, or API behavior changes
* acceptance validation

Minimum coverage should include:

* happy path
* invalid input
* missing data
* duplicate data
* corrupted data

Tests must use temporary directories or test fixtures. Tests must not read from or write to a real `~/.codex`.
