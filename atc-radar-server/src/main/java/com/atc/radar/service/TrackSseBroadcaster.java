package com.atc.radar.service;

import com.atc.radar.model.AlertEvent;
import com.atc.radar.model.TrackData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TrackSseBroadcaster {

    private final ConcurrentHashMap<String, SseEmitter> emitterRegistry = new ConcurrentHashMap<>(32);
    private final CopyOnWriteArrayList<SseEmitter> emitterList = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    private final AtomicInteger clientCount = new AtomicInteger(0);

    public TrackSseBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter createEmitter(String clientId) {
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> removeEmitter(clientId, emitter, "completed"));
        emitter.onTimeout(() -> removeEmitter(clientId, emitter, "timeout"));
        emitter.onError(e -> removeEmitter(clientId, emitter, "error: " + e.getMessage()));

        emitterRegistry.put(clientId, emitter);
        emitterList.add(emitter);
        clientCount.incrementAndGet();

        log.info("SSE client connected: {}, total: {}", clientId, clientCount.get());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            removeEmitter(clientId, emitter, "initial_send_failed");
        }

        return emitter;
    }

    private void removeEmitter(String clientId, SseEmitter emitter, String reason) {
        boolean removed = emitterRegistry.remove(clientId, emitter);
        if (removed) {
            emitterList.remove(emitter);
            clientCount.decrementAndGet();
            log.debug("SSE disconnected: {} ({})", clientId, reason);
        }
    }

    public void broadcast(TrackData track) {
        if (emitterList.isEmpty()) {
            return;
        }

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(track);
        } catch (Exception e) {
            log.error("Failed to serialize track data: {}", e.getMessage());
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("track")
                .data(eventData);

        List<SseEmitter> failed = null;

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                if (failed == null) {
                    failed = new ArrayList<>(4);
                }
                failed.add(emitter);
            }
        }

        if (failed != null) {
            for (SseEmitter failedEmitter : failed) {
                evictEmitter(failedEmitter);
            }
        }
    }

    public void broadcastHeartbeat() {
        if (emitterList.isEmpty()) {
            return;
        }

        String heartbeat = "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("heartbeat")
                .data(heartbeat);

        List<SseEmitter> failed = null;

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                if (failed == null) {
                    failed = new ArrayList<>(4);
                }
                failed.add(emitter);
            }
        }

        if (failed != null) {
            for (SseEmitter failedEmitter : failed) {
                evictEmitter(failedEmitter);
            }
        }
    }

    private void evictEmitter(SseEmitter emitter) {
        List<Map.Entry<String, SseEmitter>> toRemove = new ArrayList<>(1);
        emitterRegistry.forEachEntry(1, entry -> {
            if (entry.getValue() == emitter) {
                toRemove.add(entry);
            }
        });

        for (Map.Entry<String, SseEmitter> entry : toRemove) {
            removeEmitter(entry.getKey(), emitter, "send_failed");
        }

        try {
            emitter.completeWithError(new IOException("evicted"));
        } catch (Exception ignored) {
        }
    }

    public int getConnectedClients() {
        return clientCount.get();
    }

    public void broadcastAlert(AlertEvent alert) {
        if (emitterList.isEmpty()) {
            return;
        }

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Failed to serialize alert data: {}", e.getMessage());
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("alert")
                .data(eventData);

        List<SseEmitter> failed = null;

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                if (failed == null) {
                    failed = new ArrayList<>(4);
                }
                failed.add(emitter);
            }
        }

        if (failed != null) {
            for (SseEmitter failedEmitter : failed) {
                evictEmitter(failedEmitter);
            }
        }

        log.debug("Broadcast alert {} to {} clients", alert.getAlertId(), clientCount.get());
    }
}
