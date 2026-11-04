-- Bus booking, part 2: the "your bus leaves in about 2 hours" reminder goes once per ticket, and
-- passengers on one trip board at different stops and times, so it is tracked on the ticket rather
-- than on the trip (bus_trips.reminder_sent_at stays unused by the customer reminder).
ALTER TABLE public.bus_tickets ADD COLUMN reminder_sent_at timestamp(6) without time zone;

-- The reminder job looks for confirmed tickets still waiting for theirs.
CREATE INDEX idx_bus_tickets_reminder_due ON public.bus_tickets (trip_id)
    WHERE reminder_sent_at IS NULL AND status IN ('CONFIRMED', 'PARTIALLY_CANCELLED');

-- The sweeper looks for captured payments whose ticket never got confirmed.
CREATE INDEX idx_bus_payments_captured ON public.bus_payments (ticket_id) WHERE status = 'CAPTURED';
CREATE INDEX idx_bus_refunds_payment ON public.bus_refunds (payment_id);
