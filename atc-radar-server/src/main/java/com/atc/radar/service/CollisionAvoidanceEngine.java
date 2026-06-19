package com.atc.radar.service;

import com.atc.radar.config.CollisionAlertProperties;
import com.atc.radar.model.AlertEvent;
import com.atc.radar.model.ConflictPrediction;
import com.atc.radar.model.TrackData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "atc.radar.collision.enabled", havingValue = "true", matchIfMissing = true)
public class CollisionAvoidanceEngine {

    private static final String ALERT_KEY_PREFIX = "atc:alert:";
    private static final String ALERT_SET_KEY = "atc:alerts:active";
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double NAUTICAL_MILE_TO_METERS = 1852.0;
    private static final double FOOT_TO_METERS = 0.3048;

    private final TrackService trackService;
    private final TrackSseBroadcaster sseBroadcaster;
    private final CollisionAlertProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<String, AlertEvent> activeAlerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAlertTimes = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread engineThread;
    private final AtomicLong scanCount = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);

    @PostConstruct
    public void start() {
        running.set(true);
        engineThread = new Thread(this::runEngine, "Collision-Avoidance-Engine");
        engineThread.setDaemon(true);
        engineThread.setPriority(Thread.MAX_PRIORITY - 2);
        engineThread.start();
        log.info("Collision Avoidance Engine started. " +
                        "Prediction window: {}s, step: {}s, " +
                        "H-threshold: {}m ({}NM), V-threshold: {}m ({}ft)",
                properties.getPredictionWindowSeconds(),
                properties.getTimeStepSeconds(),
                properties.getHorizontalThresholdMeters(),
                properties.getHorizontalThresholdMeters() / NAUTICAL_MILE_TO_METERS,
                properties.getVerticalThresholdMeters(),
                properties.getVerticalThresholdMeters() / FOOT_TO_METERS);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (engineThread != null) {
            engineThread.interrupt();
            try {
                engineThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Collision Avoidance Engine stopped. Scans: {}, Alerts: {}",
                scanCount.get(), alertsGenerated.get());
    }

    private void runEngine() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                long startTime = System.nanoTime();
                scanCount.incrementAndGet();

                List<TrackData> tracks = getQualifiedTracks();

                if (tracks.size() >= 2) {
                    performCollisionDetection(tracks);
                }

                cleanupExpiredAlerts();

                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                long sleepTime = properties.getScanIntervalMs() - elapsedMs;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Collision detection error: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private List<TrackData> getQualifiedTracks() {
        List<TrackData> allTracks = trackService.getAllTracks();
        List<TrackData> qualified = new ArrayList<>(allTracks.size());

        for (TrackData track : allTracks) {
            if (track.getStatus() != TrackData.TrackStatus.ACTIVE) continue;
            if (track.getLatitude() == null || track.getLongitude() == null) continue;
            if (track.getGroundSpeed() == null || track.getGroundSpeed() < 10) continue;
            if (track.getHeading() == null) continue;
            qualified.add(track);
        }

        return qualified;
    }

    private void performCollisionDetection(List<TrackData> tracks) {
        int n = tracks.size();

        for (int i = 0; i < n - 1; i++) {
            TrackData trackA = tracks.get(i);
            for (int j = i + 1; j < n; j++) {
                TrackData trackB = tracks.get(j);

                if (isSameTrack(trackA, trackB)) continue;

                ConflictPrediction prediction = predictConflict(trackA, trackB);

                if (prediction.isConflict()) {
                    handleConflict(trackA, trackB, prediction);
                }
            }
        }
    }

    private boolean isSameTrack(TrackData a, TrackData b) {
        return a.getTrackId().equals(b.getTrackId());
    }

    public ConflictPrediction predictConflict(TrackData trackA, TrackData trackB) {
        double latA = Math.toRadians(trackA.getLatitude());
        double lonA = Math.toRadians(trackA.getLongitude());
        double altA = trackA.getAltitude() != null ? trackA.getAltitude() : 0;

        double latB = Math.toRadians(trackB.getLatitude());
        double lonB = Math.toRadians(trackB.getLongitude());
        double altB = trackB.getAltitude() != null ? trackB.getAltitude() : 0;

        double headingA = Math.toRadians(trackA.getHeading() != null ? trackA.getHeading() : 0);
        double headingB = Math.toRadians(trackB.getHeading() != null ? trackB.getHeading() : 0);

        double speedA = trackA.getGroundSpeed() != null ? trackA.getGroundSpeed() * 0.514444 : 0;
        double speedB = trackB.getGroundSpeed() != null ? trackB.getGroundSpeed() * 0.514444 : 0;

        double verticalRateA = trackA.getVerticalRate() != null ? trackA.getVerticalRate() * 0.00508 : 0;
        double verticalRateB = trackB.getVerticalRate() != null ? trackB.getVerticalRate() * 0.00508 : 0;

        double currentHDist = haversineDistance(latA, lonA, latB, lonB);
        double currentVDist = Math.abs(altA - altB);

        if (currentHDist > properties.getTcaHorizontalWarningMeters()
                && currentVDist > properties.getTcaVerticalWarningMeters()) {
            double bearingTo = calculateBearing(latA, lonA, latB, lonB);
            double closingAngle = Math.abs(normalizeAngle(bearingTo - headingA));
            if (closingAngle > Math.PI / 2) {
                return ConflictPrediction.noConflict(trackA.getTrackId(), trackB.getTrackId());
            }
        }

        double velAx = speedA * Math.sin(headingA);
        double velAy = speedA * Math.cos(headingA);
        double velBx = speedB * Math.sin(headingB);
        double velBy = speedB * Math.cos(headingB);

        double relVx = velAx - velBx;
        double relVy = velAy - velBy;
        double relSpeed = Math.sqrt(relVx * relVx + relVy * relVy);

        if (relSpeed < 0.1) {
            if (currentHDist < properties.getHorizontalThresholdMeters()
                    || currentVDist < properties.getVerticalThresholdMeters()) {
                return buildConflict(trackA, trackB, currentHDist, currentVDist, 0, relSpeed);
            }
            return ConflictPrediction.noConflict(trackA.getTrackId(), trackB.getTrackId());
        }

        double dLat = latB - latA;
        double dLon = (lonB - lonA) * Math.cos((latA + latB) / 2.0);
        double distY = dLat * EARTH_RADIUS_METERS;
        double distX = dLon * EARTH_RADIUS_METERS;

        double tca = -(distX * relVx + distY * relVy) / (relSpeed * relSpeed);
        tca = Math.max(0, Math.min(tca, properties.getPredictionWindowSeconds()));

        double minHDist = Double.MAX_VALUE;
        double minVDist = Double.MAX_VALUE;
        double tAtMin = 0;

        for (int t = 0; t <= properties.getPredictionWindowSeconds(); t += properties.getTimeStepSeconds()) {
            double latAtT = latA + (velAy * t) / EARTH_RADIUS_METERS;
            double lonAtT = lonA + (velAx * t) / (EARTH_RADIUS_METERS * Math.cos(latAtT));
            double altAtT = altA + verticalRateA * t;

            double latBtT = latB + (velBy * t) / EARTH_RADIUS_METERS;
            double lonBtT = lonB + (velBx * t) / (EARTH_RADIUS_METERS * Math.cos(latBtT));
            double altBtT = altB + verticalRateB * t;

            double hDist = haversineDistance(latAtT, lonAtT, latBtT, lonBtT);
            double vDist = Math.abs(altAtT - altBtT);

            double riskScore = (hDist / properties.getHorizontalThresholdMeters())
                    + (vDist / properties.getVerticalThresholdMeters());

            if (riskScore < (minHDist / properties.getHorizontalThresholdMeters()
                    + minVDist / properties.getVerticalThresholdMeters())) {
                minHDist = hDist;
                minVDist = vDist;
                tAtMin = t;
            }

            if (hDist < properties.getHorizontalThresholdMeters()
                    && vDist < properties.getVerticalThresholdMeters()) {
                return buildConflict(trackA, trackB, hDist, vDist, t, relSpeed);
            }
        }

        if (minHDist < properties.getHorizontalThresholdMeters()
                || minVDist < properties.getVerticalThresholdMeters()) {
            return buildConflict(trackA, trackB, minHDist, minVDist, tAtMin, relSpeed);
        }

        if (tca > 0 && tca <= properties.getPredictionWindowSeconds()) {
            double latAtTca = latA + (velAy * tca) / EARTH_RADIUS_METERS;
            double lonAtTca = lonA + (velAx * tca) / (EARTH_RADIUS_METERS * Math.cos(latAtTca));
            double latBtTca = latB + (velBy * tca) / EARTH_RADIUS_METERS;
            double lonBtTca = lonB + (velBx * tca) / (EARTH_RADIUS_METERS * Math.cos(latBtTca));
            double hDistAtTca = haversineDistance(latAtTca, lonAtTca, latBtTca, lonBtTca);
            double vDistAtTca = Math.abs((altA + verticalRateA * tca) - (altB + verticalRateB * tca));

            if (hDistAtTca < properties.getTcaHorizontalWarningMeters()
                    && vDistAtTca < properties.getTcaVerticalWarningMeters()) {
                ConflictPrediction pred = buildConflict(trackA, trackB, hDistAtTca, vDistAtTca, tca, relSpeed);
                pred.setConflict(false);
                return pred;
            }
        }

        return ConflictPrediction.noConflict(trackA.getTrackId(), trackB.getTrackId());
    }

    private ConflictPrediction buildConflict(TrackData a, TrackData b,
                                              double hDist, double vDist, double tca, double relSpeed) {
        double bearing = calculateBearing(
                Math.toRadians(a.getLatitude()), Math.toRadians(a.getLongitude()),
                Math.toRadians(b.getLatitude()), Math.toRadians(b.getLongitude()));

        return ConflictPrediction.builder()
                .trackIdA(a.getTrackId())
                .trackIdB(b.getTrackId())
                .horizontalDistanceAtTca(hDist)
                .verticalDistanceAtTca(vDist)
                .timeToClosestApproach(tca)
                .bearing(Math.toDegrees(bearing))
                .relativeSpeed(relSpeed * 1.94384)
                .conflict(true)
                .predictedTime(Instant.now().plusSeconds((long) tca))
                .build();
    }

    private void handleConflict(TrackData trackA, TrackData trackB, ConflictPrediction prediction) {
        String alertId = AlertEvent.generateAlertId(trackA.getTrackId(), trackB.getTrackId());

        long now = System.currentTimeMillis();
        Long lastAlert = lastAlertTimes.get(alertId);
        if (lastAlert != null && (now - lastAlert) < properties.getMinSeparationSeconds() * 1000L) {
            return;
        }

        AlertEvent.AlertSeverity severity = determineSeverity(prediction);
        AlertEvent.AlertType type = determineAlertType(prediction);

        double midLat = (trackA.getLatitude() + trackB.getLatitude()) / 2;
        double midLon = (trackA.getLongitude() + trackB.getLongitude()) / 2;
        double midAlt = ((trackA.getAltitude() != null ? trackA.getAltitude() : 0)
                + (trackB.getAltitude() != null ? trackB.getAltitude() : 0)) / 2;

        AlertEvent alert = AlertEvent.builder()
                .alertId(alertId)
                .type(type)
                .severity(severity)
                .status(AlertEvent.AlertStatus.ACTIVE)
                .primaryTrackId(trackA.getTrackId())
                .primaryCallsign(trackA.getCallsign())
                .primaryAltitude(trackA.getAltitude())
                .secondaryTrackId(trackB.getTrackId())
                .secondaryCallsign(trackB.getCallsign())
                .secondaryAltitude(trackB.getAltitude())
                .predictedHorizontalDistanceMeters(prediction.getHorizontalDistanceAtTca())
                .predictedVerticalDistanceMeters(prediction.getVerticalDistanceAtTca())
                .predictedTimeToClosestApproachSeconds(prediction.getTimeToClosestApproach())
                .horizontalThresholdMeters(properties.getHorizontalThresholdMeters())
                .verticalThresholdMeters(properties.getVerticalThresholdMeters())
                .latitude(midLat)
                .longitude(midLon)
                .altitude(midAlt)
                .predictedTime(prediction.getPredictedTime())
                .createdAt(Instant.now())
                .build();

        activeAlerts.put(alertId, alert);
        lastAlertTimes.put(alertId, now);
        alertsGenerated.incrementAndGet();

        saveAlertToRedis(alert);
        sseBroadcaster.broadcastAlert(alert);

        log.warn("COLLISION ALERT [{}] {} - {}: TCA={}s, H={}m ({}NM), V={}m ({}ft)",
                severity,
                trackA.getCallsign() != null ? trackA.getCallsign() : trackA.getTrackId(),
                trackB.getCallsign() != null ? trackB.getCallsign() : trackB.getTrackId(),
                String.format("%.0f", prediction.getTimeToClosestApproach()),
                String.format("%.0f", prediction.getHorizontalDistanceAtTca()),
                String.format("%.1f", prediction.getHorizontalDistanceAtTca() / NAUTICAL_MILE_TO_METERS),
                String.format("%.0f", prediction.getVerticalDistanceAtTca()),
                String.format("%.0f", prediction.getVerticalDistanceAtTca() / FOOT_TO_METERS));
    }

    private AlertEvent.AlertSeverity determineSeverity(ConflictPrediction p) {
        double hRatio = p.getHorizontalDistanceAtTca() / properties.getHorizontalThresholdMeters();
        double vRatio = p.getVerticalDistanceAtTca() / properties.getVerticalThresholdMeters();
        double tca = p.getTimeToClosestApproach();

        if (tca < 30 && (hRatio < 0.5 || vRatio < 0.5)) {
            return AlertEvent.AlertSeverity.CRITICAL;
        } else if (tca < 60 && (hRatio < 1.0 || vRatio < 1.0)) {
            return AlertEvent.AlertSeverity.HIGH;
        } else if (tca < 90) {
            return AlertEvent.AlertSeverity.MEDIUM;
        } else {
            return AlertEvent.AlertSeverity.LOW;
        }
    }

    private AlertEvent.AlertType determineAlertType(ConflictPrediction p) {
        double hRatio = p.getHorizontalDistanceAtTca() / properties.getHorizontalThresholdMeters();
        double vRatio = p.getVerticalDistanceAtTca() / properties.getVerticalThresholdMeters();

        if (hRatio < 0.3 && vRatio < 0.3) {
            return AlertEvent.AlertType.TCAS_RESOLUTION_ADVISORY;
        } else if (hRatio < 0.6 && vRatio < 0.6) {
            return AlertEvent.AlertType.TCAS_TRAFFIC_ADVISORY;
        } else if (hRatio < 1.0) {
            return AlertEvent.AlertType.PROXIMITY_WARNING;
        } else {
            return AlertEvent.AlertType.CONFLICT_PREDICTION;
        }
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return Math.atan2(y, x);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private void saveAlertToRedis(AlertEvent alert) {
        try {
            String key = ALERT_KEY_PREFIX + alert.getAlertId();
            redisTemplate.opsForValue().set(key, alert,
                    Duration.ofSeconds(properties.getAlertTtlSeconds()));
            redisTemplate.opsForSet().add(ALERT_SET_KEY, alert.getAlertId());
        } catch (Exception e) {
            log.warn("Failed to save alert to Redis: {}", e.getMessage());
        }
    }

    private void cleanupExpiredAlerts() {
        Instant now = Instant.now();
        Duration ttl = Duration.ofSeconds(properties.getAlertTtlSeconds());
        List<String> expiredIds = new ArrayList<>();

        activeAlerts.forEach(1, (id, alert) -> {
            if (Duration.between(alert.getCreatedAt(), now).compareTo(ttl) > 0
                    && alert.getStatus() == AlertEvent.AlertStatus.ACTIVE) {
                alert.setStatus(AlertEvent.AlertStatus.EXPIRED);
                expiredIds.add(id);
            }
        });

        for (String id : expiredIds) {
            activeAlerts.remove(id);
            try {
                redisTemplate.delete(ALERT_KEY_PREFIX + id);
                redisTemplate.opsForSet().remove(ALERT_SET_KEY, id);
            } catch (Exception e) {
                log.warn("Failed to remove expired alert from Redis: {}", e.getMessage());
            }
        }
    }

    public List<AlertEvent> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    public AlertEvent acknowledgeAlert(String alertId) {
        AlertEvent alert = activeAlerts.get(alertId);
        if (alert != null && alert.getStatus() == AlertEvent.AlertStatus.ACTIVE) {
            alert.setStatus(AlertEvent.AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(Instant.now());
            saveAlertToRedis(alert);
            sseBroadcaster.broadcastAlert(alert);
        }
        return alert;
    }

    public AlertEvent resolveAlert(String alertId) {
        AlertEvent alert = activeAlerts.remove(alertId);
        if (alert != null) {
            alert.setStatus(AlertEvent.AlertStatus.RESOLVED);
            saveAlertToRedis(alert);
            sseBroadcaster.broadcastAlert(alert);
        }
        return alert;
    }

    @Async
    public void triggerManualScan() {
        List<TrackData> tracks = getQualifiedTracks();
        if (tracks.size() >= 2) {
            performCollisionDetection(tracks);
        }
    }

    public Map<String, Object> getEngineStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("scanCount", scanCount.get());
        stats.put("alertsGenerated", alertsGenerated.get());
        stats.put("activeAlerts", activeAlerts.size());
        stats.put("running", running.get());
        return stats;
    }
}
