package com.atc.radar.model;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class TrackData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String trackId;

    private String callsign;

    private String targetAddress;

    private Double latitude;

    private Double longitude;

    private Double altitude;

    private Double heading;

    private Double groundSpeed;

    private Double verticalRate;

    private Integer trackNumber;

    private RadarSource source;

    private Integer category;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastUpdate;

    private TrackStatus status;

    public enum RadarSource {
        PRIMARY_RADAR,
        SECONDARY_RADAR,
        MODE_S,
        ADS_B,
        MULTILATERATION,
        FUSED
    }

    public enum TrackStatus {
        ACTIVE,
        COASTING,
        DROPPED,
        PREDICTED
    }
}
