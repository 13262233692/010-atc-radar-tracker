package com.atc.radar.controller;

import com.atc.radar.model.AlertEvent;
import com.atc.radar.service.CollisionAvoidanceEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final CollisionAvoidanceEngine engine;

    @GetMapping
    public ResponseEntity<List<AlertEvent>> getActiveAlerts() {
        return ResponseEntity.ok(engine.getActiveAlerts());
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertEvent> acknowledgeAlert(@PathVariable String alertId) {
        AlertEvent alert = engine.acknowledgeAlert(alertId);
        if (alert != null) {
            return ResponseEntity.ok(alert);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<AlertEvent> resolveAlert(@PathVariable String alertId) {
        AlertEvent alert = engine.resolveAlert(alertId);
        if (alert != null) {
            return ResponseEntity.ok(alert);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        engine.triggerManualScan();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "scanning");
        response.put("engineStats", engine.getEngineStats());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEngineStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("engine", engine.getEngineStats());
        stats.put("activeAlerts", engine.getActiveAlerts().size());
        return ResponseEntity.ok(stats);
    }
}
