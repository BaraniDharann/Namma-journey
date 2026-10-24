-- City commission and driver payouts (part 3 of the driver programme).
--
-- 1. What the platform keeps from a City ride by an independent driver, per city. Frozen onto
--    each ledger entry when the ride completes, so changing it never rewrites past earnings.
ALTER TABLE public.cities
    ADD COLUMN commission_percent numeric(5,2) NOT NULL DEFAULT 10,
    ADD CONSTRAINT cities_commission_range CHECK (commission_percent >= 0 AND commission_percent <= 50);

-- 2. Where an independent driver is paid. AES-256-GCM ciphertext written by the application
--    (EncryptedStringConverter); the database never sees the UPI ID itself.
ALTER TABLE public.drivers
    ADD COLUMN payout_upi_id character varying(512);

-- 3. The ledger between the platform and each independent driver. Balance = SUM(amount):
--    positive, the platform owes the driver; negative, the driver owes the platform.
--      RIDE_EARNING     +(fare - commission)  ride paid to the platform (online or UPI)
--      CASH_COMMISSION  -commission           ride paid in cash to the driver
--      PAYOUT           -amount               owner paid the driver (reference = UTR)
--      COLLECTION       +amount               driver paid the owner what they owed
CREATE TABLE public.driver_ledger_entries (
    id                 bigserial PRIMARY KEY,
    driver_id          bigint                 NOT NULL REFERENCES public.drivers (id),
    booking_id         uuid,
    type               character varying(20)  NOT NULL,
    amount             numeric(12,2)          NOT NULL,
    fare               numeric(12,2),
    commission_percent numeric(5,2),
    commission         numeric(12,2),
    reference          character varying(64),
    note               character varying(255),
    created_by         character varying(255) NOT NULL,
    created_at         timestamp(6) without time zone NOT NULL DEFAULT now(),
    CONSTRAINT driver_ledger_type_check
        CHECK (type IN ('RIDE_EARNING', 'CASH_COMMISSION', 'PAYOUT', 'COLLECTION')),
    CONSTRAINT driver_ledger_ride_has_booking
        CHECK (type NOT IN ('RIDE_EARNING', 'CASH_COMMISSION') OR booking_id IS NOT NULL),
    CONSTRAINT driver_ledger_transfer_has_reference
        CHECK (type NOT IN ('PAYOUT', 'COLLECTION') OR reference IS NOT NULL)
);

-- One ride entry per ride, whatever retries or races happen.
CREATE UNIQUE INDEX uq_driver_ledger_ride ON public.driver_ledger_entries (booking_id, type)
    WHERE booking_id IS NOT NULL;
CREATE INDEX idx_driver_ledger_driver ON public.driver_ledger_entries (driver_id, created_at DESC);
