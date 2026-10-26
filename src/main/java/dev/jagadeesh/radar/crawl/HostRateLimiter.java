package dev.jagadeesh.radar.crawl;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Politeness: at most one request per host per interval, shared by every crawl job in the process.
 * Each caller atomically reserves the next free slot for the host, then sleeps until it.
 */
public class HostRateLimiter {

    public interface Sleeper {
        void sleepNanos(long nanos) throws InterruptedException;
    }

    private final ConcurrentMap<String, Long> nextFreeSlot = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    public HostRateLimiter() {
        this(System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
    }

    public HostRateLimiter(LongSupplier nanoTime, Sleeper sleeper) {
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    public void acquire(String host, Duration interval) throws InterruptedException {
        long[] slot = new long[1];
        nextFreeSlot.compute(UrlNormalizer.siteKey(host), (h, next) -> {
            long now = nanoTime.getAsLong();
            long start = next == null || next < now ? now : next;
            slot[0] = start;
            return start + interval.toNanos();
        });
        long wait = slot[0] - nanoTime.getAsLong();
        if (wait > 0) {
            sleeper.sleepNanos(wait);
        }
    }
}
