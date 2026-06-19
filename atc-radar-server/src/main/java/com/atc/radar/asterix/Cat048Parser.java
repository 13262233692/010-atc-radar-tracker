package com.atc.radar.asterix;

import com.atc.radar.model.TrackData;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Cat048Parser {

    public static final int CATEGORY = 48;
    private static final double LAT_LON_SCALE = 180.0 / (1 << 25);
    private static final double ALTITUDE_SCALE = 25.0;
    private static final double HEADING_SCALE = 360.0 / (1 << 16);
    private static final double GROUND_SPEED_SCALE = 0.257222;

    public static List<TrackData> parse(byte[] data) {
        List<TrackData> tracks = new ArrayList<>();
        int offset = 3;

        while (offset < data.length) {
            TrackData track = parseRecord(data, offset);
            if (track != null) {
                tracks.add(track);
                offset += getRecordLength(data, offset);
            } else {
                break;
            }
        }

        return tracks;
    }

    private static int getRecordLength(byte[] data, int startOffset) {
        int offset = startOffset;
        int fspecIndex = 0;
        do {
            if (offset >= data.length) return 1;
            fspecIndex++;
            offset++;
        } while ((data[offset - 1] & 0x01) != 0 && fspecIndex < 8);

        int len = fspecIndex;
        int bitIndex = 0;
        for (int f = 0; f < fspecIndex; f++) {
            for (int b = 7; b > 0; b--) {
                int bit = (data[startOffset + f] >> b) & 0x01;
                if (bit == 1) {
                    len += getItemLength(bitIndex);
                }
                bitIndex++;
            }
        }
        return len;
    }

    private static int getItemLength(int dataItem) {
        return switch (dataItem) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            case 3 -> 3;
            case 4 -> 1;
            case 5 -> 3;
            case 6 -> 2;
            case 7 -> 2;
            case 8 -> 4;
            case 9 -> 3;
            case 10 -> 1;
            case 11 -> 2;
            case 12 -> 2;
            case 13 -> 1;
            case 14 -> 2;
            case 15 -> 2;
            case 16 -> 4;
            case 17 -> 1;
            case 18 -> 1;
            case 19 -> 1;
            case 20 -> 2;
            case 21 -> 2;
            case 22 -> 1;
            case 23 -> 1;
            case 24 -> 4;
            case 25 -> 4;
            case 26 -> 3;
            case 27 -> 7;
            case 28 -> 1;
            case 29 -> 1;
            case 30 -> 2;
            case 31 -> 1;
            case 32 -> 1;
            case 33 -> 1;
            case 34 -> 2;
            case 35 -> 4;
            case 36 -> 6;
            case 37 -> 3;
            case 38 -> 2;
            case 39 -> 1;
            case 40 -> 2;
            case 41 -> 4;
            case 42 -> 1;
            case 43 -> 3;
            case 44 -> 1;
            case 45 -> 1;
            case 46 -> 1;
            case 47 -> 1;
            default -> 1;
        };
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
                    .source(TrackData.RadarSource.SECONDARY_RADAR)
                    .timestamp(Instant.now())
                    .lastUpdate(Instant.now())
                    .status(TrackData.TrackStatus.ACTIVE);

            int itemIndex = 0;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int sac = BitUtils.getUnsignedByte(data, offset);
                int sic = BitUtils.getUnsignedByte(data, offset + 1);
                offset += 3;
                builder.trackId("SAC" + sac + "SIC" + sic + "-");
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int trackNumber = BitUtils.getUnsignedShort(data, offset) & 0x0FFF;
                builder.trackNumber(trackNumber);
                builder.trackId((builder.build().getTrackId() != null ? builder.build().getTrackId() : "") + trackNumber);
                offset += 2;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                offset += 1;
            }
            itemIndex++;

            if (fspec.size() > itemIndex && fspec.get(itemIndex)) {
                int timeOfDay = (BitUtils.getUnsignedByte(data, offset) << 16)
                        | (BitUtils.getUnsignedByte(data, offset + 1) << 8)
                        | BitUtils.getUnsignedByte(data, offset + 2);
                double timeSec = timeOfDay / 128.0;
                offset += 3;
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
                int rawLat = BitUtils.getSigned24Bit(data, offset);
                int rawLon = BitUtils.getSigned24Bit(data, offset + 3);
                double latitude = rawLat * LAT_LON_SCALE;
                double longitude = rawLon * LAT_LON_SCALE;
                builder.latitude(latitude);
                builder.longitude(longitude);
                offset += 6;
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
                int rawAltitude = BitUtils.getUnsignedShort(data, offset) & 0xFFF;
                double altitude = rawAltitude * ALTITUDE_SCALE;
                builder.altitude(altitude);
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
            log.error("Error parsing CAT048 record: {}", e.getMessage());
            return null;
        }
    }
}
