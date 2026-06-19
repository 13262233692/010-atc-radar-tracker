package com.atc.radar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "atc.radar")
public class AtcRadarProperties {

    private Udp udp = new Udp();
    private Sse sse = new Sse();
    private Track track = new Track();

    @Data
    public static class Udp {
        private int port = 8600;
        private int bufferSize = 65535;
    }

    @Data
    public static class Sse {
        private long heartbeatInterval = 15000;
    }

    @Data
    public static class Track {
        private long ttlSeconds = 300;
    }
}
