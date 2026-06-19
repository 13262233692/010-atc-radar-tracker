package com.atc.radar.asterix;

public final class BitUtils {

    private BitUtils() {
    }

    public static int getBit(byte[] data, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitOffset = 7 - (bitIndex % 8);
        if (byteIndex >= data.length) {
            return 0;
        }
        return (data[byteIndex] >> bitOffset) & 0x01;
    }

    public static int getBits(byte[] data, int startBit, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 1) | getBit(data, startBit + i);
        }
        return value;
    }

    public static int getUnsignedByte(byte[] data, int offset) {
        if (offset >= data.length) {
            return 0;
        }
        return data[offset] & 0xFF;
    }

    public static int getUnsignedShort(byte[] data, int offset) {
        if (offset + 1 >= data.length) {
            return 0;
        }
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static long getUnsignedInt(byte[] data, int offset) {
        if (offset + 3 >= data.length) {
            return 0L;
        }
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | ((long) (data[offset + 3] & 0xFF));
    }

    public static int getSignedShort(byte[] data, int offset) {
        if (offset + 1 >= data.length) {
            return 0;
        }
        return (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF));
    }

    public static int getSigned24Bit(byte[] data, int offset) {
        if (offset + 2 >= data.length) {
            return 0;
        }
        int value = ((data[offset] & 0xFF) << 16)
                | ((data[offset + 1] & 0xFF) << 8)
                | (data[offset + 2] & 0xFF);
        if ((value & 0x800000) != 0) {
            value -= 0x1000000;
        }
        return value;
    }

    public static String extractString(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length && offset + i < data.length; i++) {
            char c = decodeChar(data[offset + i]);
            if (c != ' ') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    public static char decodeChar(byte b) {
        int val = b & 0x3F;
        if (val == 0x20) return ' ';
        if (val >= 0x01 && val <= 0x1A) return (char) ('A' + val - 1);
        if (val >= 0x30 && val <= 0x39) return (char) ('0' + val - 0x30);
        return ' ';
    }

    public static String toHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    public static String toBinaryString(byte b) {
        return String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
    }
}
