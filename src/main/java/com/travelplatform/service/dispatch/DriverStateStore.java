package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;

import java.util.Collection;
import java.util.Map;

/**
 * The working copy of driver state a matching round reads: Redis across instances, or memory on a
 * single one ({@link DispatchStoresConfig}). Postgres stays the truth. A card nobody has asked for
 * is loaded from it, writes follow committed changes, and {@link #reconcile()} rebuilds recent
 * cards every minute, so any drift (a lost write, a crash between commit and update) heals itself.
 */
public interface DriverStateStore {

    /** Cards for these drivers in one batched read; drivers that do not exist are absent. */
    Map<Long, DriverCard> cards(Collection<Long> ids);

    /** Vehicle, seats, switches or online status changed. The driver's state is left as it is. */
    void putProfile(Driver driver);

    /** Sets the state of a driver who has a card. One without a card is loaded fresh when next read. */
    void setState(long driverId, DriverState state);

    /** Moves the state only if it is {@code expected} now: the guard against one driver getting two rides. */
    boolean compareAndSetState(long driverId, DriverState expected, DriverState next);

    /** Rebuilds the cards of drivers seen in the last ten minutes from Postgres. */
    void reconcile();
}
