package com.casms.notification.service;

import com.casms.notification.dto.NotificationResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory SSE hub pushing real-time notification events to authenticated users.
 */
@Slf4j
@Component
public class NotificationSseHub {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final int MAX_EMITTERS_PER_USER = 3;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>());
        while (list.size() >= MAX_EMITTERS_PER_USER) {
            SseEmitter oldest = list.remove(0);
            try { oldest.complete(); } catch (Exception ignored) { /* already closed */ }
        }
        list.add(emitter);

        Runnable cleanup = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("connected", true), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            cleanup.run();
        }
        return emitter;
    }

    public void publishNotification(UUID userId, NotificationResponse notification, long unreadCount) {
        sendEvent(userId, "notification", Map.of("notification", notification, "unreadCount", unreadCount));
    }

    public void publishUnreadCount(UUID userId, long unreadCount) {
        sendEvent(userId, "unread-count", Map.of("unreadCount", unreadCount));
    }

    private void sendEvent(UUID userId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByUser.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            emittersByUser.remove(userId, list);
        }
    }
}
