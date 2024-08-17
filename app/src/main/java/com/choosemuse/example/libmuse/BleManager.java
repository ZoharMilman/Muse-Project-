package com.choosemuse.example.libmuse;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BleManager {

    private static final String TAG = "BleManager";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private Handler handler;
    private boolean scanning;

    private List<BluetoothDevice> deviceList;
    private DeviceAdapter deviceAdapter;

    public BleManager(Context context, DeviceAdapter adapter, List<BluetoothDevice> deviceList) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        this.bluetoothAdapter = bluetoothAdapter;
        this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        this.handler = new Handler();
        this.deviceList = deviceList;
        this.deviceAdapter = adapter;

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                addDevice(device);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) {
                    BluetoothDevice device = result.getDevice();
                    addDevice(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "BLE Scan failed with error code: " + errorCode);
            }
        };
    }

    public void startScan() {
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is not available");
            return;
        }

        if (!scanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, 10000);

            scanning = true;
            bluetoothLeScanner.startScan(scanCallback);
            Log.i(TAG, "BLE Scan started");
        }
    }

    public void stopScan() {
        if (scanning && bluetoothLeScanner != null) {
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.i(TAG, "BLE Scan stopped");
        }
    }

    public boolean isScanning() {
        return scanning;
    }

    private void addDevice(BluetoothDevice device) {
        if (!deviceList.contains(device)) {
            Log.d(TAG, "Items in DeviceAdapter before adding discovered device " + deviceAdapter.getItemCount());
            Log.d(TAG, "Discovered device: " + device.getName() + " [" + device.getAddress() + "]");
            Log.d(TAG, "Items in DeviceAdapter after adding discovered device " + deviceAdapter.getItemCount());
            deviceList.add(device);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Current Device List " + getDeviceList());
                    deviceAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    public List<BluetoothDevice> getDeviceList() {
        return deviceList;
    }
}
