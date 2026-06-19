package com.atc.radar.controller;

import com.atc.radar.model.TrackData;
import com.atc.radar.service.TrackSseBroadcaster;
import com.atc.radar.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;
    private final TrackSseBroadcaster sseBroadcaster;

    @GetMapping
    public ResponseEntity<List<TrackData>> getAllTracks() {
        return ResponseEntity.ok(trackService.getAllTracks());
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<TrackData> getTrack(@PathVariable String trackId) {
        TrackData track = trackService.getTrack(trackId);
        if (track != null) {
            return ResponseEntity.ok(track);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{trackId}")
    public ResponseEntity<Void> removeTrack(@PathVariable String trackId) {
        trackService.removeTrack(trackId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/simulate")
    public ResponseEntity<TrackData> simulateTrack(@RequestBody TrackData track) {
        trackService.updateTrack(track);
        return ResponseEntity.ok(track);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeTracks", trackService.getActiveTrackCount());
        stats.put("connectedClients", sseBroadcaster.getConnectedClients());
        return ResponseEntity.ok(stats);
    }
}
