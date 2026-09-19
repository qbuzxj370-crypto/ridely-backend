# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Ridely (라이들리): a Spring Boot backend for an AI cycling-route recommender built around the Han River bike paths in Seoul. It fuses Korea Tourism Organization data (TourAPI) with public cycling infrastructure data (water fountains, repair shops, Ttareungyi bike-share stations, accident-prone zones) and uses an LLM ("Coach Ridely") to design routes and write coaching commentary. Built for the 2026 관광데이터 활용 공모전 competition. MVP service area is bounded to the Han River Seoul section (Arahan River Lock ~ Jamsil, ~40km) — see `mvp-area` bounds in `application.yml`.

Code comments throughout this repo are written in Korean and are often load-bearing: they record *why* a decision was made (measured tradeoffs, past incidents, rejected alternatives), not what the code does. Read them before changing the surrounding logic, and preserve that style (explain non-obvious "why", not "what") when adding your own.

## Commands

```bash
# env setup (once)
cp .env.example .env   # fill in keys — see .env.example for the full list

# DB (PostgreSQL 16 + PostGIS + pgvector via docker-compose.yml)
docker compose up -d

# run the app (local profile is the default via SPRING_PROFILES_ACTIVE)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# on Windows:
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# build (deploy.yml packages with -DskipTests; ci.yml already ran ./mvnw -B verify on the PR)
./mvnw clean package

# run all tests (spins up a shared static Testcontainers PostgreSQL/PostGIS container — Docker must be running)
./mvnw test

# run a single test class / method
./mvnw test -Dtest=RidingSummaryTest
./mvnw test -Dtest=CourseDesignResolverTest#resolvesFallbackWhenLlmFails

# health check
curl http://localhost:8080/api/v1/health
```

There is no lint/format command configured (no Checkstyle/Spotless plugin in `pom.xml`). Style convention: 4-space indent, LF line endings, UTF-8, `kr.ridely.{domain}` packages.

## Architecture

### Layering

`controller` → `service` (interface + `*Impl`) → `dao` (MyBatis mapper interfaces, XML in `src/main/resources/mapper/`). PostGIS/pgvector operations bypass MyBatis and use `JdbcClient` with raw SQL directly in DAO classes instead (ADR-002: MyBatis for simple CRUD, JdbcClient for spatial/vector operators). `infra/{llm,ors,kakao,taas,seoul,tourapi,seed}` holds external API clients and one-off data-ingest PoC controllers. `dto/{domain}` holds request/response DTOs; `vo` holds entities; `common` holds the API envelope, error codes, and shared utils (`GeoDistance`, `GeometryUtils`).

Domains: `auth`, `user`, `route`, `poi`, `savedRoute`, `rideHistory`, plus `geo` (place search) and `tour` (TourAPI passthrough).

### API response envelope

Every endpoint returns `ApiResponse<T>` (`common/ApiResponse.java`): `{ success, data, error }`. Controllers return `ApiResponse.ok(dto)` on success; `GlobalExceptionHandler` catches `BusinessException` (thrown with an `ErrorCode`) and builds the error envelope — controllers should throw rather than build error responses by hand. Error codes are centralized in `common/ErrorCode.java` (`{DOMAIN}_{NNN}` + HTTP status + Korean message); when a new failure case appears, add one code there rather than reusing an unrelated one — see the comments in that file for the reasoning behind existing codes (e.g. why 0 POI results is 200 not an error, why route-area-boundary vs no-candidates-in-area are different codes).

### Auth

Stateless JWT (`config/Jwt*`), no server-side sessions. `SecurityConfig.PUBLIC_PATHS` is the allowlist of unauthenticated routes — several are public by product design, not oversight (route recommendation and geo-search work for logged-out users so people can try the product before signing up; `userId` is stored as `NULL` for guest recommendations). **Any new public endpoint must be added to `PUBLIC_PATHS` explicitly**, otherwise it 401s. Access tokens are short-lived (15 min, not revocable); refresh tokens are DB-backed, rotating, and revocable.

### The route recommendation pipeline

The core feature lives in `service/RouteRecommendServiceImpl.recommend()` and runs, per request:

1. **Validate** — priority weights sum to 1 (`verifyPrioritySum`), start/end are inside the MVP bounding box (`verifyInServiceArea`), target distance is physically reachable given straight-line distance (`verifyReachable`).
2. **Idempotency replay** — checked *after* validation (so a bad request never consumes the client's idempotency key) via `RecommendationReplayStore`, a short-lived (20 min) cache keyed by client-supplied `Idempotency-Key`, distinct from `recommendation_cache`'s longer-lived purpose.
3. **Candidate collection** — `InfraCandidateCollector` gathers nearby POIs/infrastructure within a radius derived from target distance (`RouteProperties`).
4. **Course design** — `CourseDesignResolver` asks an LLM (`CourseDesignClient`) to pick waypoints from the candidates; falls back to `FallbackCourseDesigner` (rule-based) on call failure or when the LLM's chosen waypoint IDs don't exist in the candidate set (retries once before falling back — see the class Javadoc for why the retry is gated on *all* waypoints being invalid, not just one).
5. **Routing** — `OrsClient` (OpenRouteService, self-hosted-gateway now at `api.heigit.org`) draws the actual road geometry through the waypoints, with one avoid-danger-zone attempt that silently falls back to unavoidance if the avoided geometry makes the route unroutable (`routeWithAvoidFallback`). If the routed distance falls short of the target (common — waypoint selection alone converges on 51-62% of target in measurements), `extendIfShort` inserts one out-and-back extension point on the bike path opposite the destination; this correction happens exactly once, not in a convergence loop.
6. **Coaching commentary** — `CoachCommentResolver` / `CoachCommentClient` generate the AI narration, with the same fallback-on-failure pattern as course design (`TemplateCommentFactory`).
7. Response assembly, persistence (`RouteDao.insert`), and idempotency-store write.

If *either* course design or commentary fell back to rules/templates, the whole response is marked `aiProvider = "FALLBACK"` — partial fallback is not surfaced separately because the client's handling is the same either way (never label a partially-rule-based response as AI-authored).

LLM daily call volume is capped service-wide (`ridely.ai.llm.daily-call-limit`, tracked in `LlmCallBudget`) independent of per-IP rate limiting — it exists to keep aggregate usage under the free-tier quota even when multiple users are active simultaneously.

### LLM provider switching

`spring.ai.model.chat` is the Spring AI multi-model selector. Local/test use `google-genai` (Gemini) via API key; `application-prod.yml` overrides to `bedrock-converse` (Claude on Bedrock, via EC2 instance-role credentials, no static AWS keys anywhere). Whichever profile is active, exactly one chat model bean must be enabled — see the extensive comments in `application.yml` around `spring.ai.model.chat` for why omitting it breaks startup once more than one Spring AI starter is on the classpath.

### Testing

`AbstractIntegrationTest` boots one shared static Testcontainers PostGIS container per JVM run (not per test) and lets Flyway apply the real migrations from `src/main/resources/db/migration` against it — this is deliberate: an earlier setup let integration tests initialize schema independently of Flyway, and dev/prod migrations silently diverged from what tests exercised. Extend it for anything needing real DB/spatial behavior. It forces `spring.ai.model.chat=google-genai` with a dummy API key (tests don't call the LLM) purely so Spring context loads without ambiguity, and fixes `ridely.jwt.secret` since there's no default in the repo (secrets are never given defaults here — see `application.yml`, missing required env vars should fail startup, not silently work with a demo value).

### Database

PostgreSQL 16 + PostGIS (spatial) + pgvector (RAG, not yet activated — see commented-out `spring.ai.vectorstore` block in `application.yml`). Schema changes go through Flyway migrations in `src/main/resources/db/migration/V*.sql` — **never edit an already-applied migration file**, its checksum is validated on startup and a mismatch fails boot. Seed CSVs (`db/seed/`) are gitignored and fetched per-developer; paths are configurable (`ridely.seed.*`) because Korean filenames are sensitive to OS locale encoding.

### Configuration

`application.yml` is heavily commented with the reasoning behind non-obvious values (LLM model/timeout choices, candidate radius/count tuning backed by measured data, why certain accident-danger levels are excluded from avoidance routing, etc.) — read the inline comments before changing a tuned constant, they usually record what was already tried. Profile-specific overrides live in `application-prod.yml`; there is no `application-local.yml` in the repo (gitignored, per-developer).
