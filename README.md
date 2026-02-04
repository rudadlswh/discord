# discord

Kotlin-based Discord clone MVP scaffold.

## Included (server skeleton)
- Multi-user voice channel signaling (WebRTC)
- Text chat (WebSocket + REST history)
- Friend requests (add/accept/reject)
- Channel create/join
- Oracle DB (Docker)

> Audio/video/screen share is handled by WebRTC clients. The server only relays signaling.

## Layout
- `server/`: Ktor backend
- `db/init/`: Oracle schema
- `docker-compose.yml`: Oracle DB container
- `docs/`: architecture/API docs

## Run
### 1) Oracle DB
```
docker compose up -d
```

Default connection:
- URL: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`
- USER: `discord`
- PASSWORD: `discord`

### 2) Server
Gradle must be installed.
```
cd server
gradle run
```

Optional env vars:
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_DRIVER`
- `DB_AUTO_MIGRATE` (true/false)
- `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`

Default port: `8080`

## Next steps
- Add Android client (WebRTC)
- Add TURN server (coturn)
- Add SFU for large channels

See `docs/architecture.md`, `docs/api.md` for details.
