package com.atc.radar.simulator;

import com.atc.radar.model.TrackData;
import com.atc.radar.service.TrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@ConditionalOnProperty(name = "atc.radar.simulator.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class TrackSimulator {

    private final TrackService trackService;
    private final List<SimulatedTrack> simulatedTracks = new ArrayList<>();
    private static final String[] AIRLINES = {"CCA", "CES", "CSN", "CHH", "CSZ", "CXA", "CBJ", "CGO", "CDG", "AAL", "UAL", "DAL", "BAW", "AFR", "DLH", "KLM"};
    private static final String[] AIRCRAFT_TYPES = {"B738", "B77W", "A320", "A321", "A359", "B789", "A333", "E190"};

    @PostConstruct
    public void init() {
        Random random = ThreadLocalRandom.current();
        int numTracks = 15;

        for (int i = 0; i < numTracks; i++) {
            String airline = AIRLINES[random.nextInt(AIRLINES.length)];
            int flightNum = 1000 + random.nextInt(8999);
            String callsign = airline + flightNum;

            SimulatedTrack track = new SimulatedTrack();
            track.trackId = "SIM-" + (i + 1);
            track.callsign = callsign;
            track.targetAddress = String.format("%06X", 0x400000 + random.nextInt(0xFFFFF));
            track.latitude = 25.0 + random.nextDouble() * 20.0;
            track.longitude = 105.0 + random.nextDouble() * 30.0;
            track.altitude = 3000.0 + random.nextDouble() * 9000.0;
            track.heading = random.nextDouble() * 360.0;
            track.groundSpeed = 200.0 + random.nextDouble() * 300.0;
            track.verticalRate = (random.nextDouble() - 0.5) * 2000.0;
            track.trackNumber = 100 + i;
            track.category = random.nextBoolean() ? 48 : 62;

            double headingRad = Math.toRadians(track.heading);
            track.latSpeed = Math.cos(headingRad) * track.groundSpeed * 0.00025;
            track.lonSpeed = Math.sin(headingRad) * track.groundSpeed * 0.00025 / Math.cos(Math.toRadians(track.latitude));

            simulatedTracks.add(track);
        }

        log.info("Initialized {} simulated tracks", simulatedTracks.size());
    }

    @Scheduled(fixedRate = 1000)
    public void simulateMovement() {
        for (SimulatedTrack sim : simulatedTracks) {
            sim.latitude += sim.latSpeed;
            sim.longitude += sim.lonSpeed;

            if (sim.latitude > 55.0) sim.latitude = 25.0;
            if (sim.latitude < 25.0) sim.latitude = 55.0;
            if (sim.longitude > 140.0) sim.longitude = 105.0;
            if (sim.longitude < 105.0) sim.longitude = 140.0;

            sim.heading += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
            if (sim.heading < 0) sim.heading += 360.0;
            if (sim.heading >= 360.0) sim.heading -= 360.0;

            double headingRad = Math.toRadians(sim.heading);
            sim.latSpeed = Math.cos(headingRad) * sim.groundSpeed * 0.00025;
            sim.lonSpeed = Math.sin(headingRad) * sim.groundSpeed * 0.00025 / Math.cos(Math.toRadians(sim.latitude));

            sim.altitude += (ThreadLocalRandom.current().nextDouble() - 0.5) * 50.0;
            sim.altitude = Math.max(1000.0, Math.min(13000.0, sim.altitude));

            TrackData track = TrackData.builder()
                    .trackId(sim.trackId)
                    .callsign(sim.callsign)
                    .targetAddress(sim.targetAddress)
                    .latitude(sim.latitude)
                    .longitude(sim.longitude)
                    .altitude(sim.altitude)
                    .heading(sim.heading)
                    .groundSpeed(sim.groundSpeed)
                    .verticalRate(sim.verticalRate)
                    .trackNumber(sim.trackNumber)
                    .source(sim.category == 48 ? TrackData.RadarSource.SECONDARY_RADAR : TrackData.RadarSource.FUSED)
                    .category(sim.category)
                    .timestamp(Instant.now())
                    .lastUpdate(Instant.now())
                    .status(TrackData.TrackStatus.ACTIVE)
                    .build();

            trackService.updateTrack(track);
        }
    }

    private static class SimulatedTrack {
        String trackId;
        String callsign;
        String targetAddress;
        double latitude;
        double longitude;
        double altitude;
        double heading;
        double groundSpeed;
        double verticalRate;
        int trackNumber;
        int category;
        double latSpeed;
        double lonSpeed;
    }
}
