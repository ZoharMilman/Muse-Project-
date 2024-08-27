package com.choosemuse.example.libmuse;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.UUID;

// This class uses a BleManager with an existing connection to a hand and handles
// sending commands to the hand.
public class HandCommands {
    private static final String TAG = "HandCommands";

    private static final String HAND_NAME = "Haifa3D";

    private static final UUID triggerServiceUUID = UUID.fromString("e0198002-7544-42c1-0000-b24344b6aa70");
    private static final UUID triggerCharacteristicUUID = UUID.fromString("e0198002-7544-42c1-0001-b24344b6aa70");

    private BleManager bleManager;

    private int currentPreset;


    public HandCommands(BleManager bleManager){
        this.bleManager = bleManager;
    }

    public void handActivatePreset(int preset) {
        BluetoothGatt gatt = bleManager.getBluetoothGatt();  // Get the GATT instance

        // Check if the BluetoothGatt is null
        if (gatt == null) {
            Log.e(TAG, "BluetoothGatt is null or device not connected");
            return;
        }

        // Check that we are connected to a device
        if (!bleManager.isDeviceConnected()) {
            Log.e(TAG, "No device connected");
            return;
        }

//        // Check that the device is a hand
//        if (bleManager.getCurrentDevice().getName().equals(HAND_NAME)) {
//            Log.e(TAG, "The device name: " + bleManager.getCurrentDevice().getName() + " is not compatible with the hand name " + HAND_NAME + " indicating that its not a hand");
//            return;
//        }

        // Get the service
        BluetoothGattService service = gatt.getService(triggerServiceUUID);
        if (service == null) {
            Log.e(TAG, "Service " + triggerServiceUUID + " not found");
            return;
        }

        // Get the characteristic
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(triggerCharacteristicUUID);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic " + triggerCharacteristicUUID + " not found");
            return;
        }

        // Convert the preset number to a byte array
        byte[] value = new byte[]{(byte) preset};

        // Set the value of the characteristic
        characteristic.setValue(value);

        // Write the value to the characteristic
        boolean success = gatt.writeCharacteristic(characteristic);

        // Log the result
        if (success) {
            currentPreset = preset;
            Log.d(TAG, "Successfully wrote to characteristic " + triggerCharacteristicUUID);
        } else {
            Log.e(TAG, "Failed to write to characteristic " + triggerCharacteristicUUID);
        }
    }

    public int getCurrentPreset(){
        return currentPreset;
    }
}
