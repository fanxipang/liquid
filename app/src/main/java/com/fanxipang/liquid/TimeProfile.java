package com.fanxipang.liquid;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Calendar;
import java.util.UUID;

public class TimeProfile {
    public static final UUID TIME_SERVICE = UUID.fromString("bf4b4aa8-2623-4c96-8064-1434f0e36881");
    public static final UUID CURRENT_TIME = UUID.fromString("2c00984b-4528-4810-8ebe-07894529a89c");
    public static UUID LOCAL_TIME_INFO = UUID.fromString("ebacfc26-ca9d-4d0b-90a7-93dac8fda2f2");
    public static UUID CLIENT_CONFIG = UUID.fromString("cd2255ff-d079-4ad0-9450-0000cb05ddcf");


    public static final byte ADJUST_NONE     = 0x0;
    public static final byte ADJUST_MANUAL   = 0x1;
    public static final byte ADJUST_EXTERNAL = 0x2;
    public static final byte ADJUST_TIMEZONE = 0x4;
    public static final byte ADJUST_DST = 0x8;

    private static final byte DST_STANDARD = 0x0;
    private static final byte DST_HALF     = 0x2;
    private static final byte DST_SINGLE   = 0x4;
    private static final byte DST_DOUBLE   = 0x8;
    private static final byte DST_UNKNOWN = (byte) 0xFF;

    private static final int FIFTEEN_MINUTE_MILLIS = 900000;
    private static final int HALF_HOUR_MILLIS = 1800000;

    private static final byte DAY_UNKNOWN = 0;
    private static final byte DAY_MONDAY = 1;
    private static final byte DAY_TUESDAY = 2;
    private static final byte DAY_WEDNESDAY = 3;
    private static final byte DAY_THURSDAY = 4;
    private static final byte DAY_FRIDAY = 5;
    private static final byte DAY_SATURDAY = 6;
    private static final byte DAY_SUNDAY = 7;

    public static BluetoothGattService createTimeService() {
        BluetoothGattService service = new BluetoothGattService(TIME_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic currentTime = new BluetoothGattCharacteristic(CURRENT_TIME,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        currentTime.addDescriptor(configDescriptor);

        BluetoothGattCharacteristic localTime = new BluetoothGattCharacteristic(LOCAL_TIME_INFO,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(currentTime);
        service.addCharacteristic(localTime);

        return service;
    }

    public static byte[] getExactTime(long timestamp, byte adjustReason) {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timestamp);

        byte[] field = new byte[10];

        int year = time.get(Calendar.YEAR);
        field[0] = (byte) (year & 0xFF);
        field[1] = (byte) ((year >> 8) & 0xFF);
        field[2] = (byte) (time.get(Calendar.MONTH) + 1);
        field[3] = (byte) time.get(Calendar.DATE);
        field[4] = (byte) time.get(Calendar.HOUR_OF_DAY);
        field[5] = (byte) time.get(Calendar.MINUTE);
        field[6] = (byte) time.get(Calendar.SECOND);
        field[7] = getDayOfWeekCode(time.get(Calendar.DAY_OF_WEEK));
        field[8] = (byte) (time.get(Calendar.MILLISECOND) / 256);
        field[9] = adjustReason;

        return field;
    }

    private static byte getDayOfWeekCode(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return DAY_MONDAY;
            case Calendar.TUESDAY:
                return DAY_TUESDAY;
            case Calendar.WEDNESDAY:
                return DAY_WEDNESDAY;
            case Calendar.THURSDAY:
                return DAY_THURSDAY;
            case Calendar.FRIDAY:
                return DAY_FRIDAY;
            case Calendar.SATURDAY:
                return DAY_SATURDAY;
            case Calendar.SUNDAY:
                return DAY_SUNDAY;
            default:
                return DAY_UNKNOWN;
        }
    }

    public static byte[] getLocalTimeInfo(long timestamp) {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timestamp);

        byte[] field = new byte[2];

        int zoneOffset = time.get(Calendar.ZONE_OFFSET) / FIFTEEN_MINUTE_MILLIS; // 15 minute intervals
        field[0] = (byte) zoneOffset;

        int dstOffset = time.get(Calendar.DST_OFFSET) / HALF_HOUR_MILLIS; // 30 minute intervals
        field[1] = getDstOffsetCode(dstOffset);

        return field;
    }

    private static byte getDstOffsetCode(int rawOffset) {
        switch (rawOffset) {
            case 0:
                return DST_STANDARD;
            case 1:
                return DST_HALF;
            case 2:
                return DST_SINGLE;
            case 4:
                return DST_DOUBLE;
            default:
                return DST_UNKNOWN;
        }
    }
}
