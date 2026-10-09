-- One driver, one trip at a time - enforced by the database, not just by the code.
--
-- Assigning a driver was read-then-write: query who is free for the dates, then insert the
-- booking with the first one. Two bookings arriving together both read "driver 1 is free" before
-- either had written, and both were given driver 1 for the same days. No amount of checking in
-- Java closes that gap, because the check and the write are separate statements. An exclusion
-- constraint does: Postgres itself refuses a second active booking whose date range overlaps an
-- existing one for the same driver, atomically, whichever code path tries it (new booking,
-- reassignment after a rejection, owner assignment, date edits). UserService catches the refusal
-- (SQLSTATE 23P01) and moves on to the next free driver.

-- btree_gist lets a GiST index combine plain equality (driver_id =) with range overlap (&&).
-- It ships with Postgres and is a "trusted" extension (PG13+), so the database owner can create
-- it without superuser rights - including on managed hosts such as Render.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Clear up any clashes that already exist, or the constraint cannot be added and the app will
-- not start. For every overlapping pair the stronger booking keeps the driver: a trip already
-- STARTED beats one CONFIRMED, which beats one PENDING; ties go to whoever booked first. The
-- loser is unassigned and put back to PENDING, which is exactly how a booking with no free driver
-- already looks, so it lands in the owner's "needs a driver" list for a manual reassignment.
WITH active AS (
    SELECT id, driver_id, from_date, to_date, booking_date,
           CASE status WHEN 'STARTED' THEN 0 WHEN 'CONFIRMED' THEN 1 ELSE 2 END AS strength
    FROM public.travel_bookings
    WHERE driver_id IS NOT NULL
      AND status IN ('PENDING', 'CONFIRMED', 'STARTED')
),
losers AS (
    SELECT DISTINCT loser.id
    FROM active loser
    JOIN active winner
      ON winner.driver_id = loser.driver_id
     AND winner.id <> loser.id
     AND daterange(winner.from_date, winner.to_date, '[]') && daterange(loser.from_date, loser.to_date, '[]')
     AND (winner.strength, winner.booking_date, winner.id) < (loser.strength, loser.booking_date, loser.id)
)
UPDATE public.travel_bookings
SET driver_id = NULL,
    status = 'PENDING'
WHERE id IN (SELECT id FROM losers);

-- '[]' makes both ends inclusive: from_date and to_date are both days the driver is busy, which
-- matches the availability query (fromDate <= :toDate AND toDate >= :fromDate). Finished,
-- cancelled and unassigned bookings are outside the WHERE, so they never block anybody.
ALTER TABLE public.travel_bookings
    ADD CONSTRAINT no_driver_overlap
    EXCLUDE USING gist (
        driver_id WITH =,
        daterange(from_date, to_date, '[]') WITH &&
    )
    WHERE (driver_id IS NOT NULL AND status IN ('PENDING', 'CONFIRMED', 'STARTED'));
