--
-- V23 - Rider safety over Telegram.
--
-- Passengers get the same free channel drivers already have (V2): ride updates, a live map of
-- the car, and a "are you safe?" check during the trip with Safe / Unsafe buttons. Unsafe (or
-- the in-app SOS button) alerts the owner's safety chat and the passenger's emergency contacts
-- with a link to the car's live position.
--

--
-- Passenger <-> Telegram binding. Same shape and same rules as the driver columns in V2: the
-- chat id is only learned when the passenger opens the bot from their personal deep link, the
-- token is a single-use expiring credential, and one Telegram account maps to one passenger.
--
ALTER TABLE public.users
    ADD COLUMN telegram_chat_id character varying(32),
    ADD COLUMN telegram_linked_at timestamp(6) without time zone,
    ADD COLUMN telegram_link_token character varying(64),
    ADD COLUMN telegram_link_token_expires_at timestamp(6) without time zone;

CREATE UNIQUE INDEX idx_users_telegram_chat_id
    ON public.users (telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL;

CREATE UNIQUE INDEX idx_users_telegram_link_token
    ON public.users (telegram_link_token)
    WHERE telegram_link_token IS NOT NULL;

--
-- People a passenger wants told when they press Unsafe. A Telegram bot cannot message a phone
-- number, so a contact is only reachable once they open the bot from the invite link the
-- passenger forwards them; until then telegram_chat_id is null and they are reached only
-- through the share-trip link the passenger sends themselves.
--
CREATE TABLE public.emergency_contacts (
    id bigserial PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    name character varying(80) NOT NULL,
    phone character varying(20) NOT NULL,
    telegram_chat_id character varying(32),
    telegram_linked_at timestamp(6) without time zone,
    invite_token character varying(64),
    invite_token_expires_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_emergency_contacts_user ON public.emergency_contacts (user_id);

CREATE UNIQUE INDEX idx_emergency_contacts_invite_token
    ON public.emergency_contacts (invite_token)
    WHERE invite_token IS NOT NULL;

--
-- One row per started trip: the state the safety monitor needs between its runs.
--
-- share_token is the credential behind the public "where is this car" link. It is random rather
-- than the booking id because whoever holds the link sees a live position.
--
CREATE TABLE public.ride_safety_sessions (
    booking_id uuid PRIMARY KEY REFERENCES public.travel_bookings (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    share_token character varying(64) NOT NULL,
    started_at timestamp(6) without time zone NOT NULL,
    next_check_at timestamp(6) without time zone NOT NULL,
    last_check_at timestamp(6) without time zone,
    last_point_at timestamp(6) without time zone,
    user_live_message_id bigint,
    ops_live_message_id bigint,
    sos_at timestamp(6) without time zone,
    ended_at timestamp(6) without time zone
);

CREATE UNIQUE INDEX idx_ride_safety_sessions_share_token ON public.ride_safety_sessions (share_token);

-- The monitor scans only live trips; ended ones are kept for the record but never read by it.
CREATE INDEX idx_ride_safety_sessions_active
    ON public.ride_safety_sessions (next_check_at)
    WHERE ended_at IS NULL;

--
-- Every "are you safe?" question asked, and what came back. Kept so the owner can see after
-- the fact what the passenger was asked, why, and when they answered.
--
CREATE TABLE public.ride_safety_checks (
    id bigserial PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES public.travel_bookings (id) ON DELETE CASCADE,
    reason character varying(24) NOT NULL,
    sent_at timestamp(6) without time zone NOT NULL,
    reminded_at timestamp(6) without time zone,
    responded_at timestamp(6) without time zone,
    response character varying(16),
    telegram_message_id bigint
);

CREATE INDEX idx_ride_safety_checks_booking ON public.ride_safety_checks (booking_id, sent_at DESC);

--
-- Where the car was, sampled every ~20 seconds while a trip is STARTED. The live position in
-- Redis only holds the latest point and expires; this keeps the path, which is what the
-- stopped-too-long check reads and what anyone investigating an SOS needs afterwards.
--
CREATE TABLE public.ride_location_trail (
    id bigserial PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES public.travel_bookings (id) ON DELETE CASCADE,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    recorded_at timestamp(6) without time zone NOT NULL
);

CREATE INDEX idx_ride_location_trail_booking ON public.ride_location_trail (booking_id, recorded_at DESC);
