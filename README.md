# **Project Title**

[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## **Introduction**
Hello everyone! Welcome to our muse project. In this project, we utilized an Android app and the Muse 2 headset to create a connection to a Haifa-3D 3D-printed hand. Using the muse's ability to detect
blinks and jaw clenching, as well as some simple signal-processing logic, we present a new way to control the hand using the Muse.

---

## **File Overview**

Here’s an explanation of the core files in this repository:

### `MainActivity.java`
This is where a lot of the magic happens. More specifically, this file contains most of the connecting, processing and general communications between the app, the Muse headset, and the hand. This
file is based mostly on the MainActivity provided by Muse in their usage example. 

#### Key Methods:
- **`onCreate(Bundle savedInstanceState)`**  
  Initializes the activity, sets the content view, and links UI components to their respective logic. This is where we create our core objects such as the BleManager, HandCommands and DeviceAdapter.
- **`onDeviceClick(BluetoothDevice device)`**
  The `onDeviceClick` method is triggered when a user selects a Bluetooth device from the list. It handles the lifecycle of the device connection by:
  
  - Logging the selected device's information for debugging purposes.
  - Disconnecting from the currently connected device (if any) to ensure a fresh connection.
  - Initiating a connection to the newly selected device using the bleManager.connectToDevice() method.
  - Stopping the Bluetooth scan to save resources, clearing the device list, and updating the UI to reflect the selected device.  

- **`onClick(View v)`**
  The onClick method handles various user interactions, triggered by button presses within the app. The method checks the ID of the clicked view and performs specific actions based on the button pressed.

  - `R.id.refresh` (Refresh Button):
    Stops and restarts the process of listening for nearby or paired Muse headbands. This ensures the list of headbands is cleared and starts fresh.
  
  - `R.id.connect` (Connect Button):
    Stops listening for other Muse headbands and attempts to connect to the headband selected in the spinner. The method also registers listeners to receive specific data packets (e.g., EEG, accelerometer, gyro) from the connected device.
  
  - `R.id.disconnect` (Disconnect Button):
    Disconnects the currently selected Muse headband if connected.
  
  - `R.id.pause` (Pause/Resume Button):
    Toggles the data transmission state between the app and the connected Muse headband. It pauses or resumes data streaming based on the current state.
  
  - `R.id.refresh_bluetooth` (Refresh Bluetooth Button):
    Disconnects from the current Bluetooth device if connected, stops scanning, clears the device list, and restarts the scan to refresh available devices.
  
  - `R.id.test_hand` (Test Hand Button):
    Activates a sequence of hand command presets at 5-second intervals, effectively testing a series of hand gestures.

- **`receiveMuseArtifactPacket(MuseArtifactPacket p, Muse muse)`** 
  The receiveMuseArtifactPacket method processes incoming artifact data from the Muse headband, such as blink and jaw clench events. It tracks the state of these artifacts and updates the UI and device behavior accordingly.
  It has a few main parts:
  - Artifact Detection:
    The method checks whether a blink or jaw clench occurred using the p.getBlink() and p.getJawClench() flags. p being the received packet.
    
  - Cooldown Management:
    It manages cooldowns for both the blink and jaw clench events to prevent multiple triggers within short intervals.
    The cooldown timers reset after a defined window (blinkCooldownWindow and jawClenchCooldownWindow).
    
  - Blink Handling:
    Tracks blink events and updates the blink count.
    After detecting a certain number of changes (3 blinks), it toggles the handCommands.enable state, enabling the app to send commands to the hand.
    
  - Jaw Clench Handling:
    If a jaw clench is detected, it activates or deactivates a preset based on the current state, such as toggling between clenched and open positions.
    
  - UI Updates:
    The method updates the UI with the current states of the headband (on/off), blink status, and jaw clench status using TextView components.


### `DeviceAdapter.java`
This is a simple adapter used for showing BLE devices in the app. 

### `ApiService.java`
The BleManager class handles Bluetooth Low Energy (BLE) scanning, connection management, and data transmission for devices. It is responsible for discovering devices, connecting to them, and reading/writing characteristics.


- Scanning for Devices (`startScan()` and `stopScan()`):
  Initiates BLE scanning to discover nearby Bluetooth devices. Devices are added to a list as they are found.
  Scanning stops automatically after 10 seconds.
  
- Connecting and Disconnecting (`connectToDevice(BluetoothDevice device)` and `disconnectFromDevice()`):
  Manages connection to a selected BLE device, including opening and closing the connection using the BluetoothGatt instance.
  
- Reading and Writing Characteristics:
  Allows reading from and writing to Bluetooth characteristics of a connected device. Supports both characteristic read and write operations, with log output for success or failure.
  
- Service and Characteristic Discovery:
  Discovers services and characteristics upon connection to a device.

This class also includes a BluetoothGattCallback to handle events like connection state changes, service discovery, characteristic read/write operations, and more.

### `HandCommands.java`
The HandCommands class handles communication with a BLE-enabled hand device via a BleManager. It provides methods to activate specific presets on the hand by writing to a Bluetooth characteristic.
It has a few important roles:

- Preset Activation (using - `handActivatePreset(int preset)`):

  Sends a command to the connected hand device to activate a specific preset.
  It retrieves the BluetoothGatt instance, checks if the device is connected, and writes the preset value to the appropriate Bluetooth characteristic using the BleManager.

- Preset Management:
  The class tracks the current preset activated on the hand using the currentPreset variable, which is updated when a new preset is activated.
  
- Key Constants:
  triggerServiceUUID and triggerCharacteristicUUID: UUIDs for the specific service and characteristic used to trigger actions on the hand device.


---
## **Contact**
Something doesn't work? Forgot to mention something? Some confusing wording? Feel free to contact us at zoharmilman@campus.technion.ac.il or david.molin@campus.technion.ac.il and we will try our best to help you :).

