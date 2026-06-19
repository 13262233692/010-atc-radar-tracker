package com.atc.radar.controller;

import com.atc.radar.config.AtcRadarProperties;
import com.atc.radar.service.TrackSseBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackStreamController {

    private final TrackSseBroadcaster sseBroadcaster;
    private final AtcRadarProperties properties;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTracks() {
        String clientId = UUID.randomUUID().toString();
        return sseBroadcaster.createEmitter(clientId);
    }

    @Scheduled(fixedRateString = "${atc.radar.sse.heartbeat-interval:15000}")
    public void sendHeartbeat() {
        sseBroadcaster.broadcastHeartbeat();
    }
}
