CREATE TABLE users (
  id VARCHAR2(36) PRIMARY KEY,
  email VARCHAR2(255) NOT NULL UNIQUE,
  username VARCHAR2(50) NOT NULL UNIQUE,
  display_name VARCHAR2(100) NOT NULL,
  password_hash VARCHAR2(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE friend_requests (
  id VARCHAR2(36) PRIMARY KEY,
  requester_id VARCHAR2(36) NOT NULL,
  addressee_id VARCHAR2(36) NOT NULL,
  status VARCHAR2(16) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  responded_at TIMESTAMP WITH TIME ZONE NULL,
  CONSTRAINT fk_friend_requests_requester FOREIGN KEY (requester_id) REFERENCES users(id),
  CONSTRAINT fk_friend_requests_addressee FOREIGN KEY (addressee_id) REFERENCES users(id)
);

CREATE INDEX idx_friend_requests_addressee ON friend_requests(addressee_id);

CREATE TABLE friendships (
  user_id VARCHAR2(36) NOT NULL,
  friend_id VARCHAR2(36) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_friendships PRIMARY KEY (user_id, friend_id),
  CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id) REFERENCES users(id)
);

CREATE TABLE channels (
  id VARCHAR2(36) PRIMARY KEY,
  name VARCHAR2(100) NOT NULL,
  type VARCHAR2(16) NOT NULL,
  created_by VARCHAR2(36) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT fk_channels_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE channel_members (
  channel_id VARCHAR2(36) NOT NULL,
  user_id VARCHAR2(36) NOT NULL,
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_channel_members PRIMARY KEY (channel_id, user_id),
  CONSTRAINT fk_channel_members_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
  CONSTRAINT fk_channel_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE messages (
  id VARCHAR2(36) PRIMARY KEY,
  channel_id VARCHAR2(36) NOT NULL,
  sender_id VARCHAR2(36) NOT NULL,
  content VARCHAR2(4000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT fk_messages_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_messages_channel ON messages(channel_id);

CREATE TABLE call_sessions (
  id VARCHAR2(36) PRIMARY KEY,
  channel_id VARCHAR2(36) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  ended_at TIMESTAMP WITH TIME ZONE NULL,
  CONSTRAINT fk_call_sessions_channel FOREIGN KEY (channel_id) REFERENCES channels(id)
);

CREATE TABLE call_participants (
  id VARCHAR2(36) PRIMARY KEY,
  call_id VARCHAR2(36) NOT NULL,
  user_id VARCHAR2(36) NOT NULL,
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  left_at TIMESTAMP WITH TIME ZONE NULL,
  audio_enabled NUMBER(1) DEFAULT 1 NOT NULL,
  video_enabled NUMBER(1) DEFAULT 0 NOT NULL,
  screenshare_enabled NUMBER(1) DEFAULT 0 NOT NULL,
  CONSTRAINT fk_call_participants_call FOREIGN KEY (call_id) REFERENCES call_sessions(id),
  CONSTRAINT fk_call_participants_user FOREIGN KEY (user_id) REFERENCES users(id)
);
