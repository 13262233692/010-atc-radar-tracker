package com.atc.radar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "atc.radar.collision")
public class CollisionAlertProperties {

    private boolean enabled = true;
    private long scanIntervalMs = 1000;
    private int predictionWindowSeconds = 120;
    private int timeStepSeconds = 5;

    private double horizontalThresholdMeters = 9260.0;
    private double verticalThresholdMeters = 304.8;

    private double tcaHorizontalWarningMeters = 18520.0;
    private double tcaVerticalWarningMeters = 457.2;

    private int alertTtlSeconds = 60;
    private int minSeparationSeconds = 30;
}
