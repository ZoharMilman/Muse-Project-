package com.choosemuse.example.libmuse;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleManager {

    private static final String TAG = "BleManager";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private Handler handler;
    private boolean scanning;

    public boolean shouldScan = true;

    private boolean isConnected = false;
    private List<BluetoothDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCallback gattCallback;
    private Context context;

    private BluetoothDevice CurrentDevice;

    public BleManager(Context context, DeviceAdapter adapter, List<BluetoothDevice> deviceList) {
        this.context = context;
        this.deviceAdapter = adapter;
        this.deviceList = deviceList;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (this.bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }

        this.bluetoothLeScanner = this.bluetoothAdapter.getBluetoothLeScanner();
        this.handler = new Handler();

        initGattCallback();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                Log.d(TAG, "Discovered device: " + device.getName() + " [" + device.getAddress() + "]");
                if (device.getName() != null){
                    addDevice(device);
                }
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

    private void initGattCallback() {
        gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    Log.i(TAG, "Connected to GATT server.");
                    bluetoothGatt = gatt;  // Store the GATT instance
                    bluetoothGatt.discoverServices(); // Start service discovery
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    Log.i(TAG, "Disconnected from GATT server.");
                    bluetoothGatt = null;  // Clear the GATT instance on disconnect
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Services discovered");

                    // Iterate through the services and characteristics
                    for (BluetoothGattService service : gatt.getServices()) {
                        Log.i(TAG, "Service UUID: " + service.getUuid());

                        // Iterate through the characteristics of each service
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            Log.i(TAG, "    Characteristic UUID: " + characteristic.getUuid());

                            // Check if the characteristic is readable
                            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                Log.i(TAG, "    This characteristic is readable");
                                gatt.readCharacteristic(characteristic); // Initiate read operation
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                }
            }

            // Utility method to interpret characteristic properties
            private String propertiesToString(int properties) {
                StringBuilder sb = new StringBuilder();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    sb.append("Read ");
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    sb.append("Write ");
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    sb.append("Write No Response ");
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    sb.append("Notify ");
                }
                if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    sb.append("Indicate ");
                }
                return sb.toString().trim();
            }
            
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] data = characteristic.getValue();
                    Log.i(TAG, "Characteristic read: " + characteristic.getUuid() + " Value: " + bytesToHex(data));

                    // Process the data as needed
                } else {
                    Log.w(TAG, "Characteristic read failed for: " + characteristic.getUuid() + " with status: " + status);
                }
            }

            // Utility method to convert byte array to hex string for logging
            private String bytesToHex(byte[] bytes) {
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Characteristic written: " + characteristic.getUuid());
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.i(TAG, "Characteristic changed: " + characteristic.getUuid());
            }

            // Handle other callback methods if needed
        };
    }

    public void startScan() {
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is not available");
            return;
        }

        if (!scanning) {
            handler.postDelayed(() -> stopScan(), 10000);

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
            deviceList.add(device);
            Log.d(TAG, "Added device: " + device.getName() + " [" + device.getAddress() + "]");

            handler.post(() -> {
                deviceAdapter.notifyDataSetChanged();
            });
        }
    }

    public List<BluetoothDevice> getDeviceList() {
        return deviceList;
    }

    public void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "BluetoothDevice is null");
            return;
        }

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        CurrentDevice = device;
        Log.i(TAG, "Connecting to device: " + device.getName() + " " + device.getAddress());
    }

    public void disconnectFromDevice() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            CurrentDevice = null;
            isConnected = false;
        }
    }

    public void writeCharacteristic(UUID serviceUuid, UUID characteristicUuid, byte[] value) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "BluetoothGatt is null");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null) {
            Log.e(TAG, "Service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found");
            return;
        }

        characteristic.setValue(value);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(characteristic);
        Log.i(TAG, "Writing value to characteristic: " + characteristicUuid);
    }

    public void readCharacteristic(UUID serviceUuid, UUID characteristicUuid) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "BluetoothGatt is null");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null) {
            Log.e(TAG, "Service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found");
            return;
        }

        bluetoothGatt.readCharacteristic(characteristic);
        Log.i(TAG, "Reading characteristic: " + characteristicUuid);
    }

    public boolean isDeviceConnected() {
        return isConnected;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public BluetoothDevice getCurrentDevice(){
        return CurrentDevice;
    }
}
