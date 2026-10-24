package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single-instance twin of {@link RedisDriverStateStore}: the same rules over a map. Every
 * change is one atomic {@code compute} on the driver's entry, which is what makes
 * {@link #compareAndSetState} a real compare-and-set.
 */
public class LocalDriverStateStore implements DriverStateStore {

    /** Drivers not read or written for this long drop out of reconcile, and out of memory. */
    static final long SEEN_WINDOW_MS = 10 * 60_000L;

    private record Entry(DriverCard card, long seenAtMillis) {
    }

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final DriverCardLoader loader;

    public LocalDriverStateStore(DriverCardLoader loader) {
        this.loader = loader;
    }

    @Override
    public Map<Long, DriverCard> cards(Collection<Long> ids) {
        Map<Long, DriverCard> found = new HashMap<>();
        List<Long> missing = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Long id : ids) {
            Entry e = entries.computeIfPresent(id, (k, old) -> new Entry(old.card(), now));
            if (e == null) {
                missing.add(id);
            } else {
                found.put(id, e.card());
            }
        }
        if (!missing.isEmpty()) {
            loader.load(missing).forEach((id, card) -> {
                // A write that landed while we were loading wins over the database snapshot.
                Entry e = entries.computeIfAbsent(id, k -> new Entry(card, now));
                found.put(id, e.card());
            });
        }
        return found;
    }

    @Override
    public void putProfile(Driver driver) {
        if (driver == null || driver.getId() == null) {
            return;
        }
        entries.computeIfPresent(driver.getId(), (id, old) ->
                new Entry(DriverCardLoader.profile(driver, old.card().state()), System.currentTimeMillis()));
    }

    @Override
    public void setState(long driverId, DriverState state) {
        entries.computeIfPresent(driverId, (id, old) ->
                new Entry(old.card().withState(state), System.currentTimeMillis()));
    }

    @Override
    public boolean compareAndSetState(long driverId, DriverState expected, DriverState next) {
        boolean[] moved = {false};
        entries.computeIfPresent(driverId, (id, old) -> {
            if (old.card().state() != expected) {
                return old;
            }
            moved[0] = true;
            return new Entry(old.card().withState(next), System.currentTimeMillis());
        });
        return moved[0];
    }

    @Override
    @Scheduled(fixedDelayString = "${app.dispatch.reconcile-interval-ms:60000}",
               initialDelayString = "${app.dispatch.reconcile-interval-ms:60000}")
    public void reconcile() {
        long cutoff = System.currentTimeMillis() - SEEN_WINDOW_MS;
        entries.entrySet().removeIf(e -> e.getValue().seenAtMillis() < cutoff);
        List<Long> recent = new ArrayList<>(entries.keySet());
        Map<Long, DriverCard> truth = loader.load(recent);
        for (Long id : recent) {
            DriverCard card = truth.get(id);
            if (card == null) {
                entries.remove(id); // the driver was deleted
            } else {
                entries.computeIfPresent(id, (k, old) -> new Entry(card, old.seenAtMillis()));
            }
        }
    }
}
