# API Overview

Base URL: http://localhost:8080
Auth: Bearer token (JWT) via `Authorization: Bearer <token>` for all non-auth endpoints.

## Auth
- POST /api/auth/register
  - body: { email, username, displayName, password }
  - response: { token, user }

- POST /api/auth/login
  - body: { email, password }
  - response: { token, user }

## Friends
- POST /api/friends/requests
  - body: { addresseeId }

- GET /api/friends/requests
  - response: list of pending friend requests

- POST /api/friends/requests/{requestId}/accept
- POST /api/friends/requests/{requestId}/reject

- GET /api/friends

## Channels + Messages
- POST /api/channels
  - body: { name, type }

- GET /api/channels

- POST /api/channels/{channelId}/join

- GET /api/channels/{channelId}/messages

- POST /api/channels/{channelId}/messages
  - body: { content }

## WebSockets
- /ws/chat/{channelId}
  - Send: { content }
  - Receive: ChatMessageResponse

- /ws/signaling/{channelId}
  - Send/Receive: SignalEnvelope
  - SignalEnvelope fields: { type, targetUserId?, fromUserId?, payload? }
  - `type` values are client-defined (offer/answer/ice/candidate/leave/etc).

