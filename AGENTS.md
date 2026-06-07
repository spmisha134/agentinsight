# AgentInsight

## Mission

Build a local-first analytics platform for OpenAI Codex usage.

The system provides:

* Session discovery
* Rollout parsing
* Cost estimation
* Search
* Dashboard analytics
* Repository analytics
* Model analytics
* Skills analytics

The source of truth is the sibling resource directory `../application-build-resources/AgentInsight/specs`.

Implementation must always follow specifications.

---

# Development Methodology

This repository follows Spec Driven Development.

Implementation order:

1. Read specification.
2. Read requirements.
3. Read acceptance criteria.
4. Read tasks.
5. Implement.
6. Create tests.
7. Validate acceptance criteria.

Never implement functionality that is not described by a specification.

If a requirement is unclear:

STOP

Request clarification through specification updates.

---

# Repository Structure

```text
agentinsight-api/
agentinsight-ui/
README.md
.gitignore
```

Only `agentinsight-api/`, `agentinsight-ui/`, `README.md`, and `.gitignore` are intended for publishing from this repository.
Project resources live outside this source tree:

```text
../application-build-resources/AgentInsight/
├── specs/
├── docs/
├── template/
└── ui structure/
```

Specifications:

Specifications are the source of truth.

Code must conform to specifications.

---

# Technology Stack

Backend:

* Java 21
* Spring Boot
* Gradle
* SQLite
* jOOQ

Frontend:

* React
* TypeScript
* Vite
* Tailwind
* Recharts

Deployment:

* Docker Compose

---

# Architecture Principles

Follow Clean Architecture.

Layers:

```text
Controller
Service
Repository
Infrastructure
```

Rules:

* Controllers remain thin.
* Services contain business logic.
* Repositories contain persistence logic.
* Infrastructure contains external integrations.

Business logic must never exist in controllers.

---

# Backend Rules

Use:

* Constructor injection
* Immutable DTOs
* Java Records where appropriate
* jOOQ for persistence

Avoid:

* Field injection
* Static mutable state
* Business logic in controllers

Required tests:

* Unit tests
* Integration tests

---

# Frontend Rules

Frontend must be feature based.

Structure:

```text
features/
  dashboard/
  sessions/
  repositories/
  costs/
  models/
  skills/
```

Each feature owns:

```text
api/
components/
hooks/
routes/
```

Avoid creating generic dumping grounds.

---

# Import Rules

AgentInsight is read-only.

Never modify:

```text
~/.codex
```

Never modify:

```text
history.jsonl
sessions/
state_*.sqlite
```

Only read and import.

---

# Cost Engine Rules

Cost calculations must originate from rollout token events.

Preferred source:

```text
token_count
```

Support:

* input tokens
* cached input tokens
* output tokens

Pricing must be configurable.

Never hardcode pricing into business logic.

---

# Search Rules

Search must support:

* prompts
* responses
* repositories
* models

Preferred implementation:

SQLite FTS5

Search results must link to sessions.

---

# Analytics Rules

Analytics must be reproducible.

Given the same source data:

The same metrics must always be generated.

No random sampling.

No hidden calculations.

All metrics must be traceable to imported data.

---

# Performance Rules

Targets:

Dashboard:

```text
10,000 sessions < 2 seconds
```

Search:

```text
100,000 messages < 5 seconds
```

Rollout parsing:

```text
streaming import
```

Avoid loading large rollout files completely into memory.

---

# Error Handling Rules

Failures must not stop imports.

Corrupt files:

* log error
* continue processing

Unknown events:

* ignore safely
* log warning

---

# Testing Rules

Every specification requires:

* Unit tests
* Integration tests
* Acceptance validation

Minimum coverage:

* Happy path
* Invalid input
* Missing data
* Duplicate data
* Corrupted data

---

# Pull Request Rules

Every PR must reference the specification being implemented.

Example:

```text
Implements:
../application-build-resources/AgentInsight/specs/020-rollout-parser
```

PR description must include:

* Requirements implemented
* Acceptance criteria satisfied
* Tests added

---

# Review Rules

Review against specification.

Questions:

1. Does implementation satisfy requirements?
2. Does implementation satisfy acceptance criteria?
3. Are tests present?
4. Is architecture respected?

Review based on specifications, not personal preference.

---

# Definition Of Done

A specification is complete when:

* Requirements implemented
* Tests passing
* Acceptance criteria satisfied
* Documentation updated
* Review completed

Only then mark the specification as complete.
