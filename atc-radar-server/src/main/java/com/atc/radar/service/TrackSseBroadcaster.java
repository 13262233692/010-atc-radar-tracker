package com.atc.radar.service;

import com.atc.radar.model.TrackData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackSseBroadcaster {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter createEmitter(String clientId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> {
            emitters.remove(clientId);
            log.debug("SSE connection completed: {}", clientId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(clientId);
            log.debug("SSE connection timeout: {}", clientId);
        });
        emitter.onError(e -> {
            emitters.remove(clientId);
            log.debug("SSE connection error: {} - {}", clientId, e.getMessage());
        });

        emitters.put(clientId, emitter);
        log.info("New SSE client connected: {}, total: {}", clientId, emitters.size());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.warn("Failed to send connect event: {}", e.getMessage());
        }

        return emitter;
    }

    public void broadcast(TrackData track) {
        if (emitters.isEmpty()) {
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

        emitters.entrySet().removeIf(entry -> {
            try {
                entry.getValue().send(event);
                return false;
            } catch (IOException e) {
                log.debug("Failed to send SSE event to client {}: {}", entry.getKey(), e.getMessage());
                entry.getValue().completeWithError(e);
                return true;
            }
        });
    }

    public void broadcastHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        String heartbeat = "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("heartbeat")
                .data(heartbeat);

        emitters.entrySet().removeIf(entry -> {
            try {
                entry.getValue().send(event);
                return false;
            } catch (IOException e) {
                entry.getValue().completeWithError(e);
                return true;
            }
        });
    }

    public int getConnectedClients() {
        return emitters.size();
    }
}
