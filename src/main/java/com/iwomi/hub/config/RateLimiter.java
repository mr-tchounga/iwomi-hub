package com.iwomi.hub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory sliding-window limiter to protect the public endpoints from abuse. */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public boolean allow(String key, int max, long windowSeconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowSeconds * 1000;
        if (hits.size() > 20000) hits.clear();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) queue.pollFirst();
            if (queue.size() >= max) return false;
            queue.addLast(now);
            return true;
        }
    }

    public static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
