package com.atc.radar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String alertId;
    private AlertType type;
    private AlertSeverity severity;
    private AlertStatus status;

    private String primaryTrackId;
    private String primaryCallsign;
    private Double primaryAltitude;
    private String secondaryTrackId;
    private String secondaryCallsign;
    private Double secondaryAltitude;

    private Double predictedHorizontalDistanceMeters;
    private Double predictedVerticalDistanceMeters;
    private Double predictedTimeToClosestApproachSeconds;

    private Double horizontalThresholdMeters;
    private Double verticalThresholdMeters;

    private Double latitude;
    private Double longitude;
    private Double altitude;

    private Instant predictedTime;
    private Instant createdAt;
    private Instant acknowledgedAt;

    public enum AlertType {
        TCAS_RESOLUTION_ADVISORY,
        TCAS_TRAFFIC_ADVISORY,
        PROXIMITY_WARNING,
        CONFLICT_PREDICTION,
        HEADING_CONVERGENCE
    }

    public enum AlertSeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    public enum AlertStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED,
        EXPIRED
    }

    public static String generateAlertId(String trackA, String trackB) {
        if (trackA.compareTo(trackB) < 0) {
            return "ALERT-" + trackA + "-" + trackB;
        } else {
            return "ALERT-" + trackB + "-" + trackA;
        }
    }
}
