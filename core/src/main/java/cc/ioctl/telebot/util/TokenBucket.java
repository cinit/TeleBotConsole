package cc.ioctl.telebot.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucket<T> {

    private static class TokenBucketEntry {
        private long lastIncrement;
        private int tokens;
    }

    public final int maxTokens;

    /**
     * in milliseconds for 1 token
     */
    public final int incrementInterval;

    private final ConcurrentHashMap<T, TokenBucketEntry> entries = new ConcurrentHashMap<>(4);

    public TokenBucket(int maxTokens, int incrementInterval) {
        this.maxTokens = maxTokens;
        this.incrementInterval = incrementInterval;
    }

    /**
     * Try to consume some tokens.
     *
     * @param key             The key to use for the token bucket.
     * @param requestedTokens The number of tokens to consume.
     * @return a positive number if there were enough tokens, 0 if there were just not enough tokens,
     * and a negative number if there were not enough tokens.
     */
    public int consume(@NotNull T key, int requestedTokens) {
        Objects.requireNonNull(key, "key == null");
        if (requestedTokens <= 0) {
            throw new IllegalArgumentException("requestedTokens must be positive");
        }
        if (requestedTokens > maxTokens) {
            throw new IllegalArgumentException("requestedTokens = " + requestedTokens + " > maxTokens = " + maxTokens);
        }
        TokenBucketEntry entry = entries.computeIfAbsent(key, k -> {
            TokenBucketEntry ent = new TokenBucketEntry();
            ent.lastIncrement = System.currentTimeMillis();
            ent.tokens = maxTokens;
            return ent;
        });
        synchronized (entry) {
            long now = System.currentTimeMillis();
            long elapsed = now - entry.lastIncrement;
            if (elapsed > incrementInterval) {
                entry.lastIncrement = now;
                entry.tokens = Math.min(entry.tokens + (int) (elapsed / incrementInterval), maxTokens);
            }
            int r = entry.tokens - requestedTokens;
            if (r >= 0) {
                entry.tokens = r;
                return r / requestedTokens;
            } else {
                return -1;
            }
        }
    }

    public int consume(@NotNull T key) {
        return consume(key, 1);
    }

    public boolean tryConsume(@NotNull T key) {
        return consume(key) > 0;
    }

    public void reset() {
        entries.clear();
    }
}
