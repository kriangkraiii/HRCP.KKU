package com.ecom.external.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limits the admin "sync now" buttons.
 *
 * <p>The scheduled jobs pace themselves, but a button does not: one publication
 * sync costs five upstream requests, so about twenty impatient clicks would
 * exhaust the 100-per-minute budget and get the whole integration throttled —
 * including the nightly job. A per-job cooldown makes that impossible without
 * getting in the way of a genuine "I need fresh data now".
 *
 * <p>Only manual triggers pass through here. Cron runs are never blocked: they
 * are already spaced hours apart, and a cooldown that could suppress them would
 * turn a convenience guard into a reliability bug.
 *
 * <p>State is in memory. A restart clears it, which is fine — restarting to
 * bypass a three-minute cooldown is not a realistic misuse, and keeping it out
 * of the database avoids a write on every button press.
 */
@Component
public class ManualSyncGuard {

    private static final Logger log = LoggerFactory.getLogger(ManualSyncGuard.class);

    private final Map<String, Instant> lastManualRun = new ConcurrentHashMap<>();

    private final Duration usersCooldown;
    private final Duration scopusCooldown;

    public ManualSyncGuard(
            @Value("${fs.sync.manual-cooldown.users-seconds:60}") long usersSeconds,
            @Value("${fs.sync.manual-cooldown.scopus-seconds:180}") long scopusSeconds) {
        this.usersCooldown = Duration.ofSeconds(Math.max(usersSeconds, 0));
        this.scopusCooldown = Duration.ofSeconds(Math.max(scopusSeconds, 0));
    }

    /**
     * Claims the right to run a manual sync now.
     *
     * <p>Claiming and checking are one step so two requests arriving together
     * cannot both pass. The caller must only start the sync when this returns
     * {@link Duration#ZERO}.
     *
     * @return {@link Duration#ZERO} when the sync may proceed, otherwise how
     *         long is left on the cooldown
     */
    public Duration claim(String syncType) {
        Duration cooldown = cooldownFor(syncType);
        if (cooldown.isZero()) {
            return Duration.ZERO;
        }

        Instant now = Instant.now();
        // The decision is carried out of the lambda in a holder rather than
        // inferred from the returned instant. Two calls in the same clock tick
        // produce equal instants, so "did the stored value become mine?" cannot
        // distinguish a fresh claim from a rejected one — and both callers
        // would be let through.
        Duration[] remaining = { Duration.ZERO };

        lastManualRun.compute(syncType, (key, previous) -> {
            if (previous == null) {
                return now;
            }
            Duration since = Duration.between(previous, now);
            if (since.compareTo(cooldown) >= 0) {
                return now;
            }
            remaining[0] = cooldown.minus(since);
            return previous;
        });

        if (!remaining[0].isZero()) {
            log.debug("Manual {} sync rejected — {} s of cooldown left", syncType, remaining[0].toSeconds());
        }
        return remaining[0];
    }

    /** Cooldown left without claiming it — for rendering the page. */
    public Duration remaining(String syncType) {
        Instant last = lastManualRun.get(syncType);
        if (last == null) {
            return Duration.ZERO;
        }
        Duration left = cooldownFor(syncType).minus(Duration.between(last, Instant.now()));
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Lets a failed run be retried immediately rather than serving out a cooldown. */
    public void release(String syncType) {
        lastManualRun.remove(syncType);
    }

    private Duration cooldownFor(String syncType) {
        return FsSyncStateTypes.SCOPUS.equals(syncType) ? scopusCooldown : usersCooldown;
    }

    /** Avoids a cycle back to the entity class for two constants. */
    static final class FsSyncStateTypes {
        static final String SCOPUS = "scopus";

        private FsSyncStateTypes() {
        }
    }
}
