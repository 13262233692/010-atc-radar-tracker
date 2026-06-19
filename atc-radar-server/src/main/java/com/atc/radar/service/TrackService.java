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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

    private final ConcurrentMap<String, TrackData> localCache = new ConcurrentHashMap<>();

    public void updateTrack(TrackData track) {
        if (track == null || track.getTrackId() == null) {
            return;
        }

        track.setLastUpdate(Instant.now());
        if (track.getStatus() == null) {
            track.setStatus(TrackData.TrackStatus.ACTIVE);
        }

        TrackData existing = localCache.get(track.getTrackId());
        if (existing != null) {
            if (track.getCallsign() == null) track.setCallsign(existing.getCallsign());
            if (track.getTargetAddress() == null) track.setTargetAddress(existing.getTargetAddress());
            if (track.getAltitude() == null) track.setAltitude(existing.getAltitude());
            if (track.getHeading() == null) track.setHeading(existing.getHeading());
            if (track.getGroundSpeed() == null) track.setGroundSpeed(existing.getGroundSpeed());
            if (track.getVerticalRate() == null) track.setVerticalRate(existing.getVerticalRate());
        }

        localCache.put(track.getTrackId(), track);
        saveToRedis(track);
        sseBroadcaster.broadcast(track);

        log.debug("Updated track: {}", track.getTrackId());
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
                track = objectMapper.convertValue(obj, new TypeReference<>() {});
                localCache.put(trackId, track);
            }
        } catch (Exception e) {
            log.warn("Failed to get track from Redis: {}", e.getMessage());
        }

        return track;
    }

    public List<TrackData> getAllTracks() {
        List<TrackData> tracks = new ArrayList<>(localCache.values());

        if (tracks.isEmpty()) {
            try {
                Set<Object> trackIds = redisTemplate.opsForSet().members(TRACK_SET_KEY);
                if (trackIds != null && !trackIds.isEmpty()) {
                    for (Object idObj : trackIds) {
                        String trackId = String.valueOf(idObj);
                        TrackData track = getTrack(trackId);
                        if (track != null) {
                            tracks.add(track);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get all tracks from Redis: {}", e.getMessage());
            }
        }

        return tracks.stream()
                .filter(t -> t.getStatus() != TrackData.TrackStatus.DROPPED)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTracks() {
        Instant now = Instant.now();
        Duration ttl = Duration.ofSeconds(properties.getTrack().getTtlSeconds());

        List<String> expiredIds = localCache.entrySet().stream()
                .filter(e -> Duration.between(e.getValue().getLastUpdate(), now).compareTo(ttl) > 0)
                .map(e -> {
                    e.getValue().setStatus(TrackData.TrackStatus.DROPPED);
                    return e.getKey();
                })
                .collect(Collectors.toList());

        for (String id : expiredIds) {
            localCache.remove(id);
            try {
                redisTemplate.delete(TRACK_KEY_PREFIX + id);
                redisTemplate.opsForSet().remove(TRACK_SET_KEY, id);
            } catch (Exception e) {
                log.warn("Failed to remove expired track from Redis: {}", e.getMessage());
            }
        }

        if (!expiredIds.isEmpty()) {
            log.info("Cleaned up {} expired tracks", expiredIds.size());
        }
    }

    public void removeTrack(String trackId) {
        localCache.remove(trackId);
        try {
            redisTemplate.delete(TRACK_KEY_PREFIX + trackId);
            redisTemplate.opsForSet().remove(TRACK_SET_KEY, trackId);
        } catch (Exception e) {
            log.warn("Failed to remove track from Redis: {}", e.getMessage());
        }
    }
}
