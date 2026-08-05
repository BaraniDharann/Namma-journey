--
-- V2 - Telegram dispatch link for drivers.
--
-- Drivers miss bookings because they cannot look at a phone while driving. The dispatch
-- notification therefore has to arrive somewhere the phone already alerts loudly and
-- reliably, with an Accept control the driver can hit in one thumb press. A Telegram bot
-- gives us that without shipping a native app: Telegram's own client is already installed,
-- already battery-whitelisted by the OS, and already holds notification permission.
--
-- Telegram addresses a person by chat_id, which we only learn when the driver opens the
-- bot via a deep link. Until then the driver is simply unreachable over this channel, so
-- the column is nullable and every send path must tolerate it being null.
--

ALTER TABLE public.drivers
    ADD COLUMN telegram_chat_id character varying(32),
    ADD COLUMN telegram_linked_at timestamp(6) without time zone,
    ADD COLUMN telegram_link_token character varying(64),
    ADD COLUMN telegram_link_token_expires_at timestamp(6) without time zone;

--
-- Inbound webhook updates arrive keyed by chat_id and must resolve to a driver on every
-- button press, so this lookup is on the hot path. Unique because one Telegram account
-- must not be able to act as two drivers: without it, a second /start from the same
-- account would silently give one person accept rights over another driver's bookings.
--
-- Partial (WHERE NOT NULL) so that the many drivers who have not linked yet do not all
-- collide on a single NULL value - Postgres would allow that anyway, but the partial index
-- also keeps the index small, holding only rows that can actually be looked up.
--
CREATE UNIQUE INDEX idx_drivers_telegram_chat_id
    ON public.drivers (telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL;

--
-- The bot learns which driver a Telegram account belongs to from the payload on the
-- /start deep link, and whoever presents that payload is granted the ability to accept and
-- reject that driver's trips. It is therefore a credential, not an identifier, and must not
-- be the driver's primary key: ids here are sequential bigints, so "?start=driver_5" would
-- let anyone walk the range and capture every driver's dispatch channel.
--
-- Instead the owner mints a random, single-use, expiring token per driver. It is cleared
-- the moment it is redeemed, so a leaked onboarding QR code stops working after first use.
--
CREATE UNIQUE INDEX idx_drivers_telegram_link_token
    ON public.drivers (telegram_link_token)
    WHERE telegram_link_token IS NOT NULL;
