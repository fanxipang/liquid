package com.fanxipang.liquid;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String CLASS_TAG = MainActivity.class.getSimpleName();
    private static final String BLUETOOTH_SUPPORT_CHECK = "BLUETOOTH_SUPPORT_CHECK";
    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2;

    private TextView currentTimeView;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

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
            Log.d(CLASS_TAG, "Bluetooth is enabled");

            // start advertising
            // start server
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
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
                    Log.d("-->HUONg", "state ON");
                    // start advertising
                    // start server
                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.d("-->HUONg", "state OFF");
                    // stop server
                    // stop advertising
                default:
                    Log.d("-->HUONg", "¯|_(ツ)_/¯");
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
}
