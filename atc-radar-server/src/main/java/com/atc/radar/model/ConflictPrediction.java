package com.atc.radar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictPrediction {

    private String trackIdA;
    private String trackIdB;
    private double horizontalDistanceAtTca;
    private double verticalDistanceAtTca;
    private double timeToClosestApproach;
    private double bearing;
    private double relativeSpeed;
    private boolean conflict;
    private Instant predictedTime;

    public static ConflictPrediction noConflict(String a, String b) {
        return ConflictPrediction.builder()
                .trackIdA(a)
                .trackIdB(b)
                .conflict(false)
                .build();
    }
}
