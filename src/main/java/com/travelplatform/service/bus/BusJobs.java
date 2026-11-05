package com.travelplatform.service.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The bus module's schedules. Each job is safe to run twice (two instances, or a rerun after a
 * crash): it only acts on rows still in the state it looks for, one transaction per row.
 */
@Component
public class BusJobs {

    private static final Logger log = LoggerFactory.getLogger(BusJobs.class);

    private final BusBookingService booking;
    private final BusTicketService tickets;
    private final BusPayoutService payouts;

    public BusJobs(BusBookingService booking, BusTicketService tickets, BusPayoutService payouts) {
        this.booking = booking;
        this.tickets = tickets;
        this.payouts = payouts;
    }

    /** Expired holds free their seats; captured payments nobody settled are settled. */
    @Scheduled(fixedDelayString = "${app.bus.sweep-interval-ms:60000}", initialDelayString = "${app.bus.sweep-initial-delay-ms:45000}")
    public void sweep() {
        run("sweeper", booking::sweep);
    }

    /** SCHEDULED → DEPARTED → COMPLETED, and the boarding reminders. */
    @Scheduled(fixedDelayString = "${app.bus.status-interval-ms:300000}", initialDelayString = "${app.bus.status-initial-delay-ms:60000}")
    public void statusAndReminders() {
        run("trip status", tickets::advanceTripStatuses);
        run("reminders", tickets::sendReminders);
    }

    /** Payouts for yesterday's completed trips. */
    @Scheduled(cron = "${app.bus.payout-cron:0 0 6 * * *}", zone = "Asia/Kolkata")
    public void payouts() {
        run("payouts", payouts::createPayouts);
    }

    private static void run(String name, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException e) {
            log.warn("Bus {} job failed: {}", name, e.toString());
        }
    }
}
