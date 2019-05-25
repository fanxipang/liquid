package com.fanxipang.liquid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String BLUETOOTH_SUPPORT_CHECK = "BLUETOOTH_SUPPORT_CHECK";

    private TextView currentTimeView;
    private BluetoothManager bluetoothManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentTimeView = (TextView) findViewById(R.id.text_current_time);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (!checkBluetoothSupported(bluetoothAdapter)) {
            finish();
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
}
