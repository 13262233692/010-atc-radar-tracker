package com.atc.radar.asterix;

import com.atc.radar.model.TrackData;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Cat062Parser {

    public static final int CATEGORY = 62;
    private static final double LAT_LON_SCALE_WGS84 = 180.0 / (1 << 25);
    private static final double ALTITUDE_FL_SCALE = 25.0;
    private static final double HEADING_SCALE = 360.0 / (1 << 16);
    private static final double GROUND_SPEED_SCALE = 0.257222;
    private static final double VERTICAL_RATE_SCALE = 6.25;

    public static List<TrackData> parse(byte[] data) {
        List<TrackData> tracks = new ArrayList<>();
        int offset = 3;

        while (offset < data.length) {
            TrackData track = parseRecord(data, offset);
            if (track != null) {
                tracks.add(track);
                offset += calculateRecordLength(data, offset);
            } else {
                break;
            }
        }

        return tracks;
    }

    private static int calculateRecordLength(byte[] data, int startOffset) {
        int offset = startOffset;
        int fspecLen = 0;
        byte fspecByte;
        List<Boolean> fspec = new ArrayList<>();

        do {
            if (offset >= data.length) return 1;
            fspecByte = data[offset++];
            fspecLen++;
            for (int b = 7; b > 0; b--) {
                fspec.add(((fspecByte >> b) & 0x01) == 1);
            }
        } while ((fspecByte & 0x01) != 0 && fspecLen < 8);

        int len = fspecLen;
        int[] itemLengths = {
            1, 1, 1, 2, 3, 1, 2, 4,
            4, 1, 3, 2, 2, 4, 1, 1,
            2, 1, 3, 1, 1, 3, 2, 1,
            6, 2, 2, 4, 1, 1, 1, 2,
            1, 1, 1, 2, 8, 1, 1, 2,
            2, 2, 4, 4, 4, 1, 1, 1,
            1, 1, 1, 1, 2, 1, 1, 1,
            1, 2, 1, 2, 2, 2, 1, 1
        };

        for (int i = 0; i < fspec.size() && i < itemLengths.length; i++) {
            if (fspec.get(i)) {
                len += itemLengths[i];
            }
        }

        return len;
    }

    private static TrackData parseRecord(byte[] data, int startOffset) {
        try {
            int offset = startOffset;
            List<Boolean> fspec = new ArrayList<>();
            int fspecLen = 0;
            byte fspecByte;

            do {
                if (offset >= data.length) return null;
                fspecByte = data[offset++];
                fspecLen++;
                for (int b = 7; b > 0; b--) {
                    fspec.add(((fspecByte >> b) & 0x01) == 1);
                }
            } while ((fspecByte & 0x01) != 0 && fspecLen < 8);

            TrackData.TrackDataBuilder builder = TrackData.builder()
                    .category(CATEGORY)
                    .source(TrackData.RadarSource.FUSED)
                    .timestamp(Instant.now())
                    .lastUpdate(Instant.now())
                    .status(TrackData.TrackStatus.ACTIVE);

            int itemIndex = 0;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int trackNumber = BitUtils.getUnsignedShort(data, offset) & 0x0FFF;
                builder.trackNumber(trackNumber);
                builder.trackId("TRK-" + trackNumber);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int timeOfDay = (BitUtils.getUnsignedByte(data, offset) << 16)
                        | (BitUtils.getUnsignedByte(data, offset + 1) << 8)
                        | BitUtils.getUnsignedByte(data, offset + 2);
                offset += 3;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int rawLat = BitUtils.getSigned24Bit(data, offset);
                int rawLon = BitUtils.getSigned24Bit(data, offset + 3);
                double latitude = rawLat * LAT_LON_SCALE_WGS84;
                double longitude = rawLon * LAT_LON_SCALE_WGS84;
                builder.latitude(latitude);
                builder.longitude(longitude);
                offset += 6;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int rawAlt = BitUtils.getUnsignedShort(data, offset) & 0x0FFF;
                double altitude = rawAlt * ALTITUDE_FL_SCALE;
                builder.altitude(altitude);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 3;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int rawHeading = BitUtils.getUnsignedShort(data, offset);
                double heading = rawHeading * HEADING_SCALE;
                builder.heading(heading);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int rawGs = BitUtils.getUnsignedShort(data, offset);
                double groundSpeed = rawGs * GROUND_SPEED_SCALE;
                builder.groundSpeed(groundSpeed);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 4;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int rawVr = BitUtils.getSignedShort(data, offset);
                double verticalRate = rawVr * VERTICAL_RATE_SCALE;
                builder.verticalRate(verticalRate);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int targetAddress = (BitUtils.getUnsignedByte(data, offset) << 16)
                        | (BitUtils.getUnsignedByte(data, offset + 1) << 8)
                        | BitUtils.getUnsignedByte(data, offset + 2);
                builder.targetAddress(String.format("%06X", targetAddress));
                offset += 3;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 3;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                String callsign = BitUtils.extractString(data, offset, 6);
                builder.callsign(callsign);
                offset += 6;
            }
            itemIndex++;

            TrackData track = builder.build();
            if (track.getLatitude() != null && track.getLongitude() != null) {
                return track;
            }

            return null;
        } catch (Exception e) {
            log.error("Error parsing CAT062 record: {}", e.getMessage());
            return null;
        }
    }
}
