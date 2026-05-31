# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

**Aidea** is a Spring Boot 3.x collaborative document editing platform with real-time Yjs-based synchronization and Gemini AI feedback. Users authenticate via GitHub OAuth2, work in team spaces, and can request AI-driven document improvements.

## Build & Run

```bash
# Start MySQL only (Spring runs locally)
docker compose -f docker-compose.dev.yml up -d

# Build (skip tests)
./gradlew build -x test

# Run locally (dev profile)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.aidea.aidea.SomeTest"
```

API docs available at `http://localhost:8080/swagger-ui.html` when running.

## Environment Variables

The app reads from a `.env` file via `spring-dotenv`. Required variables:

| Variable | Purpose |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL connection |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth2 |
| `JWT_SECRET` | JWT signing key |
| `FRONTEND_URL` | CORS allowed origin |
| `GEMINI_API_KEY` | Gemini 1.5 Flash API |
| `COOKIE_SECURE` | `true` in prod, `false` locally |

Dev defaults in `application.yml` use `root/root` MySQL credentials and `localhost:5173` as frontend URL.

## Architecture

### Package Structure

All source lives under `com.aidea.aidea`:
- `global/` — cross-cutting: `config/`, `security/`, `exception/`, `util/`
- `domain/` — feature modules, each with `entity/`, `repository/`, `service/`, `controller/`, `dto/`

### Domain Modules

| Module | Responsibility |
|---|---|
| `auth` | GitHub OAuth2 login, JWT access/refresh token issuance |
| `teamspace` | Team spaces and member roles (OWNER / MEMBER / VIEWER) |
| `invitation` | Email-based invite flow with expiry |
| `documents` | Document CRUD, Yjs real-time sync, PDF/MD export |
| `aifeedback` | Gemini AI feedback lifecycle |

### Security & Auth Flow

- **OAuth2**: GitHub → `CustomOAuth2UserService` → `OAuth2AuthenticationSuccessHandler` issues JWT pair stored in HttpOnly cookies (`access_token`, `refresh_token`)
- **JWT filter**: `JwtAuthenticationFilter` validates `access_token` cookie or `Authorization: Bearer` header on every request
- **WebSocket**: `/ws/**` is `permitAll()` in Spring Security; `DocumentHandshakeInterceptor` validates JWT (header, query param `?token=`, or `access_token` cookie) and resolves `docId`, `userId`, `role` into session attributes

### Real-time Collaboration (Yjs / WebSocket)

Path: `ws://host/ws/documents/{docId}`

1. **Handshake** (`DocumentHandshakeInterceptor`): authenticates and attaches `docId`, `userId`, `role` to the session
2. **Init** (`DocumentWebSocketHandler.sendDocInit`): sends `doc:init` with the stored Yjs snapshot + all pending incremental updates (both base64-encoded)
3. **Updates** (`doc:update`): incoming binary updates are pushed to `DocumentUpdateBuffer` (in-memory) and persisted to `document_updates` table; then broadcast to all other sessions for the same document
4. VIEWER role cannot send `doc:update` — rejected at handler level
5. `DocumentWebSocketHandler.pushToDocument()` is used by `GeminiService` to push AI feedback events to all sessions on the document

### AI Feedback Lifecycle (`aifeedback`)

State machine: `PENDING → QUESTIONING → ANSWERING → DONE → ACCEPTED/REJECTED` (or `FAILED`)

1. `POST /api/documents/{docId}/feedback` → `FeedbackService.initiateFeedback()` creates a `Feedback` row (status=PENDING), pushes `feedback:started` WebSocket event, and calls `GeminiService.callGemini()` asynchronously (`@Async`)
2. Gemini returns either:
   - `QUESTIONS` → status becomes QUESTIONING, pushes `feedback:questioning` with question list
   - `FEEDBACK` → status becomes DONE, pushes `feedback:ready` with revised markdown
3. If QUESTIONING: `POST /api/feedbacks/{id}/answers` → `FeedbackService.submitAnswer()` transitions to ANSWERING and calls `GeminiService.callGeminiWithAnswers()` async
4. Final answer always returns `FEEDBACK` type; revised markdown stored and status set to DONE
5. OWNER/MEMBER can accept (`feedback:resolved ACCEPTED`) or reject (`feedback:resolved REJECTED`)
6. Only one in-progress feedback per document at a time (guarded by `existsByDocumentIdAndStatusIn`)

`FeedbackEventPublisher` is an interface; `DocumentWebSocketHandler` implements it — this decouples `GeminiService` from WebSocket infrastructure.

### Error Handling

- All domain errors throw `CustomException(ErrorCode)` — `GlobalExceptionHandler` maps them to `GlobalResponse`
- `ErrorCode` enum owns the HTTP status, code string, and user-facing message
- `FeedbackService` currently also throws non-standard exceptions (`ConflictException`, `ForbiddenException`, `NotFoundException`) that need to align with the `CustomException` pattern

### Database

MySQL 8.0. JPA with `ddl-auto: update` (dev). IDs are UUID strings for documents and feedbacks; Long auto-increment for users. `document_updates` stores raw Yjs binary deltas until a batch snapshot merge compacts them.

### Production Deployment

Blue-green Docker Compose deployment on EC2 via `scripts/deploy.sh`. Nginx handles SSL termination and routing between `spring-blue` / `spring-green` containers. ECR image referenced by `$ECR_IMAGE` env var.
