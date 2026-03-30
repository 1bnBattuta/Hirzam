# Hirzam Chat

A real-time instant messaging application built with **Vert.x 5**,
**PostgreSQL** and **SockJS**.

---

## Architecture
```
MainVerticle
DatabaseVerticle   → PostgreSQL, JDBC pool, Event Bus consumers
HttpVerticle       → HTTP server, REST API, SockJS bridge
```

Verticles communicate exclusively through the **Vert.x Event Bus**.
`HttpVerticle` never touches the database directly — it sends a message
on the bus and waits for `DatabaseVerticle` to reply.
```
Browser
  │  HTTP request
  ▼
HttpVerticle  ──eventBus.request()──▶  DatabaseVerticle
                                               │
                                          MessageService
                                               │
                                          PostgreSQL
                                               │
                                       ◀──msg.reply()──
  │  HTTP response + SockJS broadcast
  ▼
All connected browsers
```

---

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (or similar like Podman)

---

## Database setup

### With Podman
```bash
podman run --name chatdb \
  -e POSTGRES_DB=hirzamdb \
  -e POSTGRES_USER=superuser \
  -e POSTGRES_PASSWORD="The world revolving" \
  -p 5432:5432 \
  -d postgres:16
```

### With Docker
```bash
docker run --name chatdb \
  -e POSTGRES_DB=hirzamdb \
  -e POSTGRES_USER=superuser \
  -e POSTGRES_PASSWORD="The world revolving" \
  -p 5432:5432 \
  -d postgres:16
```

The command is identical — just replace `podman` with `docker`.
The schema is created automatically on first startup.

Stop / start the container between sessions:
```bash
# Podman
podman stop chatdb
podman start chatdb

# Docker
docker stop chatdb
docker start chatdb
```

---

## Build and run
```bash
# Compile and run in development mode
mvn compile
mvn exec:java

# Or build a fat-jar and run it
mvn package -DskipTests
java -jar target/hirzam-1.0-SNAPSHOT.jar
```

The application starts on **http://localhost:8080**.

---

## API reference

### GET `/api/messages`
Returns the 20 most recent non-deleted messages ordered by date ascending.
```bash
curl http://localhost:8080/api/messages
```

Response `200`:
```json
[
  {
    "id": 1,
    "username": "Alice",
    "content": "Hello !",
    "created_at": "2026-02-14T10:30:00",
    "updated": false,
    "deleted": false
  }
]
```

---

### POST `/api/messages`
Creates a new message.
```bash
curl -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -d '{"username":"Alice","content":"Hello !"}'
```

Response `201`:
```json
{
  "id": 1,
  "username": "Alice",
  "content": "Hello !",
  "created_at": "2026-02-14T10:30:00",
  "updated": false,
  "deleted": false
}
```

Error `400` if `username` or `content` is missing or blank.

---

### GET `/api/message/:id`
Returns a single message by id.
```bash
curl http://localhost:8080/api/message/1
```

Response `200` — same shape as above.
Response `404` if not found or deleted.

---

### PUT `/api/message/:id`
Updates the content of a message. Sets `updated: true`.
```bash
curl -X PUT http://localhost:8080/api/message/1 \
  -H "Content-Type: application/json" \
  -d '{"content":"Hello, edited !"}'
```

Response `200` — the updated message.
Response `404` if not found or already deleted.

---

### DELETE `/api/message/:id`
Soft-deletes a message (sets `deleted: true`, row is kept in the database).
```bash
curl -X DELETE http://localhost:8080/api/message/1
```

Response `204 No Content`.
Response `404` if not found or already deleted.

---

## Real-time — SockJS / Event Bus

The server exposes a SockJS endpoint at `/eventbus`.
The following addresses are broadcast to all connected clients:

| Address             | Payload                        | Triggered by        |
|---------------------|--------------------------------|---------------------|
| `ws.newMessage`     | Full message JSON              | POST /api/messages  |
| `ws.updatedMessage` | Full updated message JSON      | PUT /api/message/:id |
| `ws.deletedMessage` | `{ "id": N }`                  | DELETE /api/message/:id |

---

## Project structure
```
src/
├── main/
│   ├── java/com/hirzam/
│   │   ├── MainVerticle.java
│   │   ├── HttpVerticle.java
│   │   ├── DatabaseVerticle.java
│   │   └── service/
│   │       ├── MessageService.java
│   │       └── impl/
│   │           └── MessageServiceImpl.java
│   └── resources/
│       └── webroot/
│           ├── index.html
│           ├── app.js
│           └── style.css
└── test/
    └── java/com/hirzam/
        └── ...
```

---

## Running tests

Tests use **HSQLDB** (in-memory) — no PostgreSQL needed.
```bash
mvn test
```

---

## Known limitations

- Database credentials are hardcoded in `MainVerticle` — move to
  environment variables or a config file.
- No authentication — any user can post, edit, or delete any message.