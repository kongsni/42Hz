-- Entity-derived initial schema, NOT a verified snapshot of the production database.
-- Existing databases require independent schema comparison and an explicit baseline at V1.
-- IDs and timestamps are supplied by the application; no database defaults are added.
CREATE TABLE rooms (
    id uuid NOT NULL,
    invite_code varchar(12) NOT NULL,
    status varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT rooms_pkey PRIMARY KEY (id),
    CONSTRAINT uk_rooms_invite_code UNIQUE (invite_code),
    CONSTRAINT ck_rooms_status CHECK (status IN ('WAITING', 'READY', 'PLAYING', 'COMPLETED'))
);

CREATE TABLE participants (
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    nickname varchar(16) NOT NULL,
    token_hash varchar(64) NOT NULL,
    CONSTRAINT participants_pkey PRIMARY KEY (id),
    CONSTRAINT uk_participants_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_participants_room FOREIGN KEY (room_id) REFERENCES rooms (id)
);

CREATE TABLE answers (
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    participant_id uuid NOT NULL,
    question_index integer NOT NULL,
    answer varchar(1000) NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT answers_pkey PRIMARY KEY (id),
    CONSTRAINT uk_answers_participant_question UNIQUE (participant_id, question_index),
    CONSTRAINT fk_answers_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_answers_participant FOREIGN KEY (participant_id) REFERENCES participants (id)
);

CREATE TABLE shared_results (
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    share_token varchar(64) NOT NULL,
    matched_count integer NOT NULL,
    different_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT shared_results_pkey PRIMARY KEY (id),
    CONSTRAINT uk_shared_result_room UNIQUE (room_id),
    CONSTRAINT uk_shared_result_token UNIQUE (share_token),
    CONSTRAINT fk_shared_results_room FOREIGN KEY (room_id) REFERENCES rooms (id)
);
