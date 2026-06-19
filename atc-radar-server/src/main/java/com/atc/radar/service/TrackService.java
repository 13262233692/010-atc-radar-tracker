package com.atc.radar.service;

import com.atc.radar.config.AtcRadarProperties;
import com.atc.radar.model.TrackData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackService {

    private static final String TRACK_KEY_PREFIX = "atc:track:";
    private static final String TRACK_SET_KEY = "atc:tracks:active";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtcRadarProperties properties;
    private final TrackSseBroadcaster sseBroadcaster;

    private final ConcurrentMap<String, TrackData> localCache = new ConcurrentHashMap<>(512, 0.75f, 64);

    private final AtomicReference<List<TrackData>> snapshotRef = new AtomicReference<>(Collections.emptyList());
    private final StampedLock snapshotLock = new StampedLock();
    private volatile long snapshotVersion = 0;
    private volatile long lastSnapshotBuildTime = 0;
    private static final long SNAPSHOT_MIN_INTERVAL_MS = 200;

    public void updateTrack(TrackData track) {
        if (track == null || track.getTrackId() == null) {
            return;
        }

        track.setLastUpdate(Instant.now());
        if (track.getStatus() == null) {
            track.setStatus(TrackData.TrackStatus.ACTIVE);
        }

        final String trackId = track.getTrackId();

        localCache.compute(trackId, (key, existing) -> {
            if (existing != null) {
                if (track.getCallsign() == null) track.setCallsign(existing.getCallsign());
                if (track.getTargetAddress() == null) track.setTargetAddress(existing.getTargetAddress());
                if (track.getAltitude() == null) track.setAltitude(existing.getAltitude());
                if (track.getHeading() == null) track.setHeading(existing.getHeading());
                if (track.getGroundSpeed() == null) track.setGroundSpeed(existing.getGroundSpeed());
                if (track.getVerticalRate() == null) track.setVerticalRate(existing.getVerticalRate());
            }
            return track;
        });

        snapshotVersion++;

        saveToRedis(track);
        sseBroadcaster.broadcast(track);

        log.debug("Updated track: {}", trackId);
    }

    private void saveToRedis(TrackData track) {
        try {
            String key = TRACK_KEY_PREFIX + track.getTrackId();
            redisTemplate.opsForValue().set(key, track,
                    Duration.ofSeconds(properties.getTrack().getTtlSeconds()));
            redisTemplate.opsForSet().add(TRACK_SET_KEY, track.getTrackId());
        } catch (Exception e) {
            log.warn("Failed to save track to Redis: {}", e.getMessage());
        }
    }

    public TrackData getTrack(String trackId) {
        TrackData track = localCache.get(trackId);
        if (track != null) {
            return track;
        }

        try {
            String key = TRACK_KEY_PREFIX + trackId;
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj != null) {
                track = objectMapper.convertValue(obj, new TypeReference<TrackData>() {});
                localCache.put(trackId, track);
                snapshotVersion++;
            }
        } catch (Exception e) {
            log.warn("Failed to get track from Redis: {}", e.getMessage());
        }

        return track;
    }

    public List<TrackData> getAllTracks() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshotBuildTime > SNAPSHOT_MIN_INTERVAL_MS) {
            rebuildSnapshot();
        }
        List<TrackData> snapshot = snapshotRef.get();
        return snapshot;
    }

    private void rebuildSnapshot() {
        long stamp = snapshotLock.tryWriteLock();
        if (stamp == 0) {
            return;
        }
        try {
            List<TrackData> result = new ArrayList<>(localCache.size());
            localCache.forEachValue(64, track -> {
                if (track.getStatus() != TrackData.TrackStatus.DROPPED) {
                    result.add(track);
                }
            });
            snapshotRef.set(Collections.unmodifiableList(result));
            lastSnapshotBuildTime = System.currentTimeMillis();
        } finally {
            snapshotLock.unlockWrite(stamp);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTracks() {
        Instant now = Instant.now();
        Duration ttl = Duration.ofSeconds(properties.getTrack().getTtlSeconds());
        List<String> expiredIds = new ArrayList<>();

        localCache.forEach(64, (id, track) -> {
            if (track.getLastUpdate() != null
                    && Duration.between(track.getLastUpdate(), now).compareTo(ttl) > 0) {
                track.setStatus(TrackData.TrackStatus.DROPPED);
                expiredIds.add(id);
            }
        });

        if (expiredIds.isEmpty()) {
            return;
        }

        for (String id : expiredIds) {
            localCache.remove(id);
            try {
                redisTemplate.delete(TRACK_KEY_PREFIX + id);
                redisTemplate.opsForSet().remove(TRACK_SET_KEY, id);
            } catch (Exception e) {
                log.warn("Failed to remove expired track from Redis: {}", e.getMessage());
            }
        }

        snapshotVersion++;
        log.info("Cleaned up {} expired tracks", expiredIds.size());
    }

    public void removeTrack(String trackId) {
        localCache.remove(trackId);
        snapshotVersion++;
        try {
            redisTemplate.delete(TRACK_KEY_PREFIX + trackId);
            redisTemplate.opsForSet().remove(TRACK_SET_KEY, trackId);
        } catch (Exception e) {
            log.warn("Failed to remove track from Redis: {}", e.getMessage());
        }
    }

    public int getActiveTrackCount() {
        return localCache.size();
    }
}
