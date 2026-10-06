package com.mcverse.jobify.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key inside a sliding time window, in memory. Good enough for one instance; with several
 * instances behind a load balancer each keeps its own counts (move to a shared store when that happens).
 */
public class SlidingWindowCounter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public SlidingWindowCounter(Clock clock, Duration window) {
        this.clock = clock;
        this.window = window;
    }

    /** Records one event for the key. */
    public void add(String key) {
        long now = clock.millis();
        events.compute(key, (k, deque) -> {
            Deque<Long> d = deque == null ? new ArrayDeque<>() : deque;
            prune(d, now);
            d.addLast(now);
            return d;
        });
        if (events.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
    }

    /** Events for the key still inside the window. */
    public int count(String key) {
        long now = clock.millis();
        Deque<Long> deque = events.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque, now);
            return deque.size();
        }
    }

    /** Seconds until the oldest event in the window expires, at least 1. */
    public long secondsUntilOldestExpires(String key) {
        long now = clock.millis();
        Deque<Long> deque = events.get(key);
        if (deque == null) {
            return 1;
        }
        synchronized (deque) {
            prune(deque, now);
            Long oldest = deque.peekFirst();
            if (oldest == null) {
                return 1;
            }
            long millisLeft = oldest + window.toMillis() - now;
            return Math.max(1, (millisLeft + 999) / 1000);
        }
    }

    public void clear(String key) {
        events.remove(key);
    }

    private void prune(Deque<Long> deque, long now) {
        long cutoff = now - window.toMillis();
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.pollFirst();
        }
    }

    private void cleanup(long now) {
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }
}
