# Discord Clone Architecture (Kotlin)

## Goals
- Multi-user voice channels with optional video and screen sharing.
- Text chat per channel with history.
- Friend requests with accept/reject.
- Oracle as the system of record.

## High-Level Components
1) Clients (Kotlin)
   - Android app using WebRTC for audio/video/screen share.
   - Uses REST for auth + CRUD and WebSocket for chat/signaling.

2) Kotlin Server (Ktor)
   - REST API: auth, friends, channels, messages.
   - WebSocket: chat broadcast, WebRTC signaling relay.
   - In-memory session registry for realtime connections.

3) Media Plane (WebRTC)
   - Peer-to-peer (or SFU if added later).
   - Signaling via /ws/signaling/{channelId}.
   - STUN/TURN required for NAT traversal.

4) Oracle DB
   - Stores users, friendships, friend_requests, channels, messages.
   - Stores call session metadata (optional).

## Realtime Flows
- Chat: client connects to /ws/chat/{channelId}, sends message -> server persists -> broadcast.
- Voice/Video/Screen Share: client connects to /ws/signaling/{channelId}.
  - Send offer/answer/ice as SignalEnvelope messages.
  - Server relays to target user or broadcasts in room.

## Next Steps (Phase 2)
- Add TURN server (coturn) to docker-compose for reliable media.
- Add SFU (e.g., mediasoup or Janus) if large group calls are required.
- Add push notifications and offline friend request handling.
- Add moderation, rate limiting, and audit logging.
