--
-- V3 - remember which drivers have declined a booking.
--
-- Rejecting a trip used to clear travel_bookings.driver_id and leave the booking PENDING
-- with nobody assigned, waiting for the owner to notice a notification and reassign by hand.
-- Reassignment now happens automatically, and that needs a memory of who already said no.
--
-- Without it the fix does not work at all: the next driver is drawn from "ACTIVE drivers with
-- no overlapping active booking", and the driver who just declined becomes available again
-- the instant their id leaves the booking - so the trip would be handed straight back to
-- them, and again after the next refusal, forever.
--
CREATE TABLE public.booking_driver_rejections (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    driver_id bigint NOT NULL,
    rejected_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT booking_driver_rejections_pkey PRIMARY KEY (id)
);

--
-- One row per (booking, driver). A driver declining the same trip twice is not new
-- information, and the uniqueness makes the insert safe to repeat.
--
-- Reassignment reads every rejection for one booking; that lookup rides on this index's
-- leading column, so no separate index on booking_id is needed.
--
CREATE UNIQUE INDEX idx_booking_driver_rejections_booking_driver
    ON public.booking_driver_rejections (booking_id, driver_id);
