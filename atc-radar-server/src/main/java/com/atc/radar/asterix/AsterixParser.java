package com.atc.radar.asterix;

import com.atc.radar.model.TrackData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AsterixParser {

    public List<TrackData> parse(byte[] data) {
        List<TrackData> tracks = new ArrayList<>();

        if (data == null || data.length < 3) {
            log.warn("ASTERIX data too short or null");
            return tracks;
        }

        int offset = 0;
        while (offset < data.length) {
            if (offset + 3 > data.length) {
                break;
            }

            int category = BitUtils.getUnsignedByte(data, offset);
            int length = BitUtils.getUnsignedShort(data, offset + 1);

            if (length < 3 || offset + length > data.length) {
                log.warn("Invalid ASTERIX block: category={}, length={}, remaining={}", category, length, data.length - offset);
                break;
            }

            byte[] blockData = new byte[length];
            System.arraycopy(data, offset, blockData, 0, length);

            try {
                switch (category) {
                    case 48 -> tracks.addAll(parseCat048(blockData));
                    case 62 -> tracks.addAll(parseCat062(blockData));
                    default -> log.debug("Unsupported ASTERIX category: {}", category);
                }
            } catch (Exception e) {
                log.error("Error parsing ASTERIX category {}: {}", category, e.getMessage(), e);
            }

            offset += length;
        }

        return tracks;
    }

    private List<TrackData> parseCat048(byte[] blockData) {
        return Cat048Parser.parse(blockData);
    }

    private List<TrackData> parseCat062(byte[] blockData) {
        return Cat062Parser.parse(blockData);
    }
}
