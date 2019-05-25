package com.fanxipang.liquid;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String CLASS_TAG = MainActivity.class.getSimpleName();
    private static final String BLUETOOTH_SUPPORT_CHECK = "BLUETOOTH_SUPPORT_CHECK";
    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2;

    private TextView currentTimeView;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private Set<BluetoothDevice> registeredDevices = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentTimeView = (TextView) findViewById(R.id.text_current_time);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (!checkBluetoothSupported(bluetoothAdapter)) {
            finish();
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(broadcastReceiver, filter);

        if(bluetoothAdapter.isEnabled()) {
            Log.w(CLASS_TAG, "Bluetooth is enabled");

            startAdvertising();
            startServer();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(timeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }

        unregisterReceiver(broadcastReceiver);
    }

    private BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            byte adjustReason;
            switch (intent.getAction()) {
                case Intent.ACTION_TIME_CHANGED:
                    adjustReason = TimeProfile.ADJUST_MANUAL;
                    break;
                case Intent.ACTION_TIMEZONE_CHANGED:
                    adjustReason = TimeProfile.ADJUST_TIMEZONE;
                    break;
                default:
                case Intent.ACTION_TIME_TICK:
                    adjustReason = TimeProfile.ADJUST_NONE;
                    break;
            }
            long now = System.currentTimeMillis();
            notifyRegisteredDevices(now, adjustReason);
            updateLocalUi(now);
        }
    };

    private void notifyRegisteredDevices(long timestamp, byte adjustReason) {
        if (registeredDevices.isEmpty()) {
            Log.i(CLASS_TAG, "No subscribers registered");
            return;
        }
        byte[] exactTime = TimeProfile.getExactTime(timestamp, adjustReason);

        Log.i(CLASS_TAG, "Sending update to " + registeredDevices.size() + " subscribers");
        for (BluetoothDevice device : registeredDevices) {
            BluetoothGattCharacteristic timeCharacteristic = bluetoothGattServer
                    .getService(TimeProfile.TIME_SERVICE)
                    .getCharacteristic(TimeProfile.CURRENT_TIME);
            timeCharacteristic.setValue(exactTime);
            bluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    private boolean checkBluetoothSupported(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(BLUETOOTH_SUPPORT_CHECK, "Device doesn't support Bluetooth");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(BLUETOOTH_SUPPORT_CHECK, "Device doesn't support Bluetooth Low Energy");
            return false;
        }

        return true;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                default:
                    // ¯\_(ツ)_/¯
            }

        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode){
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
                } else {
                    Toast.makeText(getApplicationContext(), "Could not enable Bluetooth",
                            Toast.LENGTH_LONG).show();
                }
        }
    }

    private void startAdvertising() {
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (bluetoothLeAdvertiser == null) {
            Log.w(CLASS_TAG, "Failed to start Bluetooth advertising");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(TimeProfile.TIME_SERVICE))
                .build();

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.w(CLASS_TAG, "LE Advertise started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(CLASS_TAG, "Could not start LE Advertise");
        }
    };

    private void startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback);

        if (bluetoothGattServer == null) {
            Log.w(CLASS_TAG, "Unable to create GATT server");
            return;
        }

        bluetoothGattServer.addService(TimeProfile.createTimeService());

        updateLocalUi(System.currentTimeMillis());
    }

    private BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(CLASS_TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(CLASS_TAG, "BluetoothDevice DISCONNECTED: " + device);
                registeredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid())) {
                Log.i(CLASS_TAG, "Read CurrentTime");
                bluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getExactTime(now, TimeProfile.ADJUST_NONE));
            } else if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid())) {
                Log.i(CLASS_TAG, "Read LocalTimeInfo");
                bluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getLocalTimeInfo(now));
            } else {
                // Invalid characteristic
                Log.w(CLASS_TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                bluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(CLASS_TAG, "Config descriptor read");
                byte[] returnValue;
                if (registeredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                bluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            } else {
                Log.w(CLASS_TAG, "Unknown descriptor read request");
                bluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(CLASS_TAG, "Subscribe device to notifications: " + device);
                    registeredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(CLASS_TAG, "Unsubscribe device from notifications: " + device);
                    registeredDevices.remove(device);
                }

                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(CLASS_TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

    private void updateLocalUi(long timestamp) {
        Date date = new Date(timestamp);
        String displayDate = DateFormat.getMediumDateFormat(this).format(date)
                + "\n"
                + DateFormat.getTimeFormat(this).format(date);
        currentTimeView.setText(displayDate);
    }

    private void stopServer() {
        if (bluetoothGattServer == null) return;
        bluetoothGattServer.close();
    }

    private void stopAdvertising() {
        if (bluetoothLeAdvertiser == null) return;

        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
    }
}
