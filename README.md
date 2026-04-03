# URL Shortener

A production-ready URL shortening service built with Spring Boot 3, PostgreSQL, and Redis. Demonstrates clean layered architecture, cache-aside pattern, and containerized local development.

---

## Features

- Shorten any `http://` or `https://` URL to a compact Base62 code
- Redirect via short code with HTTP 302
- Optional link expiry (per-request TTL in seconds)
- Click count analytics per short code
- Redis cache-aside with 24-hour TTL for hot-path redirects
- Global exception handling with consistent error responses
- Health and metrics via Spring Boot Actuator

---

## Architecture

```
┌─────────────┐         ┌──────────────────────────────────────────┐
│   Client    │         │              Spring Boot App              │
│  (Browser / │──HTTP──▶│                                          │
│   curl)     │         │  UrlController                           │
└─────────────┘         │      │                                   │
                        │      ▼                                   │
                        │  UrlService                              │
                        │      │                                   │
                        │      ├──── cache hit? ──▶ Redis          │
                        │      │                   (url:<code>)    │
                        │      │                                   │
                        │      └──── cache miss ──▶ UrlRepository  │
                        │                               │          │
                        │                               ▼          │
                        │                          PostgreSQL       │
                        │                          (urls table)    │
                        └──────────────────────────────────────────┘

Redirect hot path:
  GET /{code} → Redis lookup (O(1)) → 302 redirect         [cache hit]
  GET /{code} → Redis miss → PostgreSQL → cache write → 302 [cache miss]
```

---

## Tech Stack

| Layer          | Technology                        |
|----------------|-----------------------------------|
| Language       | Java 17                           |
| Framework      | Spring Boot 3.2                   |
| Database       | PostgreSQL 15                     |
| Cache          | Redis 7                           |
| ORM            | Spring Data JPA / Hibernate       |
| Validation     | Jakarta Bean Validation           |
| Boilerplate    | Lombok                            |
| Build          | Maven                             |
| Containerization | Docker + Docker Compose         |

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose

---

## Local Setup

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `localhost:5433` (mapped from container port 5432)
- Redis on `localhost:6379`

Both containers have health checks configured. Wait until they are healthy before starting the app.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

Or build and run the JAR:

```bash
./mvnw clean package -DskipTests
java -jar target/url-shortener-0.0.1-SNAPSHOT.jar
```

The app starts on `http://localhost:8080`.

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
```

---

## API Reference

### POST /api/shorten — Create a short URL

**Request body:**

| Field        | Type    | Required | Description                              |
|--------------|---------|----------|------------------------------------------|
| `url`        | string  | Yes      | The original URL (`http://` or `https://`)|
| `ttlSeconds` | number  | No       | Seconds until expiry. Omit for no expiry.|

**Example — permanent link:**

```bash
curl -s -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://github.com/abiram"}' | jq
```

**Example — link that expires in 1 hour:**

```bash
curl -s -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://github.com/abiram", "ttlSeconds": 3600}' | jq
```

**Response (201 Created):**

```json
{
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/1",
  "originalUrl": "https://github.com/abiram",
  "createdAt": "2026-04-03T10:00:00",
  "expiresAt": null
}
```

---

### GET /{shortCode} — Redirect to original URL

```bash
curl -v http://localhost:8080/1
```

Returns **302 Found** with a `Location` header pointing to the original URL.

To follow the redirect automatically:

```bash
curl -L http://localhost:8080/1
```

**Error responses:**

| Scenario          | Status | Body                                      |
|-------------------|--------|-------------------------------------------|
| Code not found    | 404    | `{"error": "Short code not found: abc"}`  |
| Link expired      | 410    | `{"error": "Short code has expired: abc"}`|

---

### GET /api/stats/{shortCode} — Click analytics

```bash
curl -s http://localhost:8080/api/stats/1 | jq
```

**Response (200 OK):**

```json
{
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/1",
  "originalUrl": "https://github.com/abiram",
  "clickCount": 42,
  "createdAt": "2026-04-03T10:00:00",
  "expiresAt": null
}
```

---

### GET /actuator/health — Health check

```bash
curl -s http://localhost:8080/actuator/health | jq
```

---

## Key Design Decisions

### Base62 Encoding

Short codes are generated by Base62-encoding the database auto-increment ID using the alphabet `[0-9A-Za-z]`. This gives:

- 1-character codes for IDs 1–61
- 2-character codes up to ID 3,843
- 7-character codes support over 3.5 trillion URLs

No random generation means no collision checks and deterministic decoding. The trade-off is that sequential IDs are slightly guessable — acceptable for a public, unauthenticated service.

### Redis Cache-Aside Pattern

On every redirect (`GET /{shortCode}`):

1. Check Redis for key `url:<shortCode>`
2. **Cache hit** → return immediately, increment click counter in PostgreSQL
3. **Cache miss** → query PostgreSQL, write result to Redis with a 24-hour TTL, then redirect

This keeps the critical redirect path at O(1) Redis lookups for warm codes, while PostgreSQL remains the source of truth.

### Docker Compose for Infrastructure

Only the stateful services (PostgreSQL, Redis) run in Docker. The Spring Boot app runs on the host JVM for fast iteration (`spring-boot:run` with hot reload). Both containers use named volumes for data persistence across restarts and are configured with health checks so dependent services start cleanly.

---

## Project Structure

```
src/main/java/com/abi/urlshortener/
├── controller/        # REST endpoints (UrlController)
├── service/           # Business logic (UrlService)
├── repository/        # JPA repository (UrlRepository)
├── entity/            # JPA entity (Url)
├── dto/               # Request/response objects
│   ├── ShortenRequest
│   ├── ShortenResponse
│   └── UrlStatsResponse
├── exception/         # Custom exceptions + GlobalExceptionHandler
└── util/              # Base62Encoder
```

---

## Future Improvements

- **Authentication** — API keys or JWT so users can manage their own links
- **Custom aliases** — Let users choose their own short code (e.g. `/my-link`)
- **QR code generation** — Return a QR code alongside the short URL on creation
- **Rate limiting** — Prevent abuse on the shorten endpoint (e.g. Bucket4j or Redis token bucket)
- **Click analytics dashboard** — Time-series click data with referrer and geo breakdown
- **Link preview** — `GET /api/preview/{shortCode}` to inspect destination before redirecting
- **Async click counting** — Decouple the counter increment from the redirect path using a message queue (Kafka / RabbitMQ) to further reduce redirect latency
- **Containerize the app** — Add the Spring Boot service to `docker-compose.yml` for a fully containerized deployment
