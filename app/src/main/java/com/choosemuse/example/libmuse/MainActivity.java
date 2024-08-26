/*
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2016
 */


package com.choosemuse.example.libmuse;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.Gyro;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Importing the BLE interface from haifa3D
import com.haifa3D.haifa3d_ble_api.BleAPICommands;
import com.haifa3D.haifa3d_ble_api.ble.BleService;

/**
 * This example will illustrate how to connect to a Muse headband,
 * register for and receive EEG data and disconnect from the headband.
 * Saving EEG data to a .muse file is also covered.
 *
 * Usage instructions:
 * 1. Pair your headband if necessary.
 * 2. Run this project.
 * 3. Turn on the Muse headband.
 * 4. Press "Refresh". It should display all paired Muses in the Spinner drop down at the
 *    top of the screen.  It may take a few seconds for the headband to be detected.
 * 5. Select the headband you want to connect to and press "Connect".
 * 6. You should see EEG and accelerometer data as well as connection status,
 *    version information and relative alpha values appear on the screen.
 * 7. You can pause/resume data transmission with the button at the bottom of the screen.
 * 8. To disconnect from the headband, press "Disconnect"
 */
public class MainActivity extends Activity implements View.OnClickListener, DeviceAdapter.OnDeviceClickListener {

    /**
     * Tag used for logging purposes.
     */
    private final String TAG = "TestLibMuseAndroid";

    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    private Muse muse;

    /**
     * The ConnectionListener will be notified whenever there is a change in
     * the connection state of a headband, for example when the headband connects
     * or disconnects.
     *
     * Note that ConnectionListener is an inner class at the bottom of this file
     * that extends MuseConnectionListener.
     */
    private ConnectionListener connectionListener;

    /**
     * The DataListener is how you will receive EEG (and other) data from the
     * headband.
     *
     * Note that DataListener is an inner class at the bottom of this file
     * that extends MuseDataListener.
     */
    private DataListener dataListener;




    /**
     * Data comes in from the headband at a very fast rate; 220Hz, 256Hz or 500Hz,
     * depending on the type of headband and the preset configuration.  We buffer the
     * data that is read until we can update the UI.
     *
     * The stale flags indicate whether or not new data has been received and the buffers
     * hold the values of the last data packet received.  We are displaying the EEG, ALPHA_RELATIVE
     * and ACCELEROMETER values in this example.
     *
     * Note: the array lengths of the buffers are taken from the comments in
     * MuseDataPacketType, which specify 3 values for accelerometer and 6
     * values for EEG and EEG-derived packets.
     */
    private final double[] eegBuffer = new double[6];
    private boolean eegStale;
    private final double[] alphaBuffer = new double[6];
    private boolean alphaStale;
    private final double[] accelBuffer = new double[3];
    private boolean accelStale;

    //TODO OUR CODE
//    private HeadTiltStateMachine headTiltMachine;

    private final double[] gyroBuffer = new double[3];

    private int gyroToggleX = 0;

    private final double gyroThresholdX = 80;

    private boolean gyroStale;

    private boolean blinkFlag;

    private boolean blinkStale;

    /**
     * We will be updating the UI using a handler instead of in packet handlers because
     * packets come in at a very high frequency and it only makes sense to update the UI
     * at about 60fps. The update functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private Handler handler;

    /**
     * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
     * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
     */
    private ArrayAdapter<String> spinnerAdapter;

    /**
     * It is possible to pause the data transmission from the headband.  This boolean tracks whether
     * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
     */
    private boolean dataTransmission = true;

    /**
     * To save data to a file, you should use a MuseFileWriter.  The MuseFileWriter knows how to
     * serialize the data packets received from the headband into a compact binary format.
     * To read the file back, you would use a MuseFileReader.
     */
    private final AtomicReference<MuseFileWriter> fileWriter = new AtomicReference<>();

    /**
     * We don't want file operations to slow down the UI, so we will defer those file operations
     * to a handler on a separate thread.
     */
    private final AtomicReference<Handler> fileHandler = new AtomicReference<>();

    /**
     * This is BLE related variables
     *
     */
    private BleManager bleManager;
    private DeviceAdapter deviceAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();

    private BleAPICommands bleAPI;
    //--------------------------------------
    // Lifecycle / Connection code for the muse


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        Log.i(TAG, "LibMuse version=" + LibmuseVersion.instance().getString());

        WeakReference<MainActivity> weakActivity =
                new WeakReference<>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));


        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Initialize the UI and BLE manager, careful to give them the same deviceList to work with
        deviceAdapter = new DeviceAdapter(this, deviceList, this);
        bleManager = new BleManager(this, deviceAdapter, deviceList);

        // Initialize the BleAPICommands object
        bleAPI = new BleAPICommands();

        // Create an Intent for the BleService
        Intent bleServiceIntent = new Intent(this, BleService.class);

        // Bind to the BLE service
        bleAPI.bind(new BleAPICommands.IBleListener() {
            @Override
            public void onConnected(BleService bleService) {
                // Handle BLE service connection
                Log.i(TAG, "Connected to BLE service");
//                bleService.mockConnect();
//                testBleAPI();
            }

            @Override
            public void onDisconnected() {
                // Handle BLE service disconnection
                Log.i(TAG, "Disconnected from BLE service");
            }
        }, this, bleServiceIntent);

        initUI();

        // Start up a thread for asynchronous file operations.
        // This is only needed if you want to do File I/O.
//        fileThread.start();

        //TODO our code. Initializes the head tilt machine.
//        headTiltMachine = new HeadTiltStateMachine();

        // Start our asynchronous updates of the UI.
        handler = new Handler(getMainLooper());
        handler.post(tickUi);
    }


    private void testBleAPI() {
        Log.i(TAG, "TESTING MODE");

//        // Example of testing the connection
//        BluetoothDevice mockDevice = ... // Create or get a mock BluetoothDevice
//        bleAPI.connect(mockDevice);
//
//        // Test other API commands, such as triggering a preset
//        bleAPI.Hand_activation_by_preset(0);  // Assuming preset number 0
//
//        // Test battery level extraction
//        int batteryLevel = bleAPI.Extract_battery_status();
//        Log.i(TAG, "Battery level: " + batteryLevel + "%");
    }



    protected void onPause() {
        super.onPause();
        // It is important to call stopListening when the Activity is paused
        // to avoid a resource leak from the LibMuse library.
        manager.stopListening();
    }

    @SuppressWarnings("unused")
    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    @Override
    public void onDeviceClick(BluetoothDevice device) {
        Log.d(TAG, "Selected device: " + device.getName() + " [" + device.getAddress() + "]");

        // Check if the BleAPICommands object and service are properly bound
        if (bleAPI != null && bleAPI.isServiceConnected()) {
            Log.i(TAG, "Starting connection lifecycle");
            // Disconnect from the current device
            bleAPI.disconnect();

            // Connect to the new device
            bleAPI.connect(device);

//            bleAPI.Hand_activation_by_preset(1);


            // Stop scanning and update UI
            bleManager.stopScan();
            deviceList.clear();
            deviceList.add(device);
            deviceAdapter.notifyDataSetChanged();
            bleManager.shouldScan = false;
            Log.i(TAG, "Connection lifecycle done");


        } else {
            Log.e(TAG, "BLE service is not connected. Cannot connect to device.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind the BLE service when the activity is destroyed
        bleAPI.unbind(this);
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.refresh) {
            // The user has pressed the "Refresh" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();
            Spinner musesSpinner = findViewById(R.id.muses_spinner);

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1 || musesSpinner.getAdapter().getCount() < 1) {
                Log.w(TAG, "There is nothing to connect to");
            } else {

                // Cache the Muse that the user has selected.
                muse = availableMuses.get(musesSpinner.getSelectedItemPosition());
                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
                muse.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
                muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
                muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
                muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);
                //TODO our code
                muse.registerDataListener(dataListener, MuseDataPacketType.GYRO);
                muse.registerDataListener(dataListener, MuseDataPacketType.ARTIFACTS);
                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }

        } else if (v.getId() == R.id.disconnect) {

            // The user has pressed the "Disconnect" button.
            // Disconnect from the selected Muse.
            if (muse != null) {
                muse.disconnect();
            }

        } else if (v.getId() == R.id.pause) {

            // The user has pressed the "Pause/Resume" button to either pause or
            // resume data transmission.  Toggle the state and pause or resume the
            // transmission on the headband.
            if (muse != null) {
                dataTransmission = !dataTransmission;
                muse.enableDataTransmission(dataTransmission);
            }

        } else if (v.getId() == R.id.refresh_bluetooth) {
            // Start BLE scan on refresh button click
            bleManager.shouldScan = true;
            bleManager.stopScan();
            Log.d(TAG, "Device list before refresh " + bleManager.getDeviceList());
            bleManager.getDeviceList().clear();
            Log.d(TAG, "Device list after refresh " + bleManager.getDeviceList());
            deviceAdapter.notifyDataSetChanged();
            bleManager.startScan();

        } else if (v.getId() == R.id.test_hand) {
            Log.d(TAG, "Test Hand Pressed");

            Log.d(TAG, "Extract battery: " + bleAPI.Extract_battery_status());
            bleAPI.Hand_activation_by_preset(0);

        } else if (v.getId() == R.id.disconnect_bluetooth){
            bleAPI.disconnect();
        }

    }


    //--------------------------------------
    // Lifecycle / Connection code for the ble interface


    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_FINE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private void ensurePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }


    //--------------------------------------
    // Muse Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
    public void museListChanged() {
        final List<Muse> list = manager.getMuses();
        spinnerAdapter.clear();
        for (Muse m : list) {
            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
        }
    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);

        // Update the UI with the change in connection state.
        handler.post(() -> {

            final TextView statusText = findViewById(R.id.con_status);
            statusText.setText(status);

            final MuseVersion museVersion = muse.getMuseVersion();
            final TextView museVersionText = findViewById(R.id.version);
            // If we haven't yet connected to the headband, the version information
            // will be null.  You have to connect to the headband before either the
            // MuseVersion or MuseConfiguration information is known.
            if (museVersion != null) {
                final String version = museVersion.getFirmwareType() + " - "
                        + museVersion.getFirmwareVersion() + " - "
                        + museVersion.getProtocolVersion();
                museVersionText.setText(version);
            } else {
                museVersionText.setText(R.string.undefined);
            }
        });

        if (current == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Muse disconnected:" + muse.getName());
            // Save the data file once streaming has stopped.
//            saveFile();
            // We have disconnected from the headband, so set our cached copy to null.
            this.muse = null;
        }
    }

    /**
     * You will receive a callback to this method each time the headband sends a MuseDataPacket
     * that you have registered.  You can use different listeners for different packet types or
     * a single listener for all packet types as we have done here.
     * @param p     The data packet containing the data from the headband (eg. EEG data)
     * @param muse  The headband that sent the information.
     */
    @SuppressWarnings("unused")
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
//        writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        @SuppressWarnings("unused") final long n = p.valuesSize();
        switch (p.packetType()) {
            case EEG:
                getEegChannelValues(eegBuffer,p);
                eegStale = true;
                break;
            case ACCELEROMETER:
                getAccelValues(p);
                accelStale = true;
                break;
            case ALPHA_RELATIVE:
                getEegChannelValues(alphaBuffer,p);
                alphaStale = true;
                break;
            //TODO our code
            case GYRO:
                getGyroValues(p);
//                headTiltMachine.updateGyroXState(gyroBuffer[0]);
//                getGyroToggleX();
                gyroStale = true;
                break;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            default:
                break;
        }
    }

    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    @SuppressWarnings("unused")
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
        //TODO our code
        final boolean isOn = p.getHeadbandOn();
        final boolean blinkFlag = p.getBlink();
        final boolean jawClenchFlag = p.getJawClench();

        // Format a message to show the change of connection state in the UI.
        final String onStatus = String.valueOf(isOn);
        Log.i(TAG, onStatus);

        final String blinkStatus = String.valueOf(blinkFlag);
        Log.i(TAG, blinkStatus);

        final String jawClenchStatus = String.valueOf(jawClenchFlag);
        Log.i(TAG, jawClenchStatus);

        // Update the UI with the change in artifact state.
        handler.post(() -> {

            final TextView onStatusText = findViewById(R.id.on_status);
            onStatusText.setText(onStatus);

            final TextView blinkStatusText = findViewById(R.id.blink_status);
            blinkStatusText.setText(blinkStatus);

            final TextView jawClenchStatusText = findViewById(R.id.jaw_clench_status);
            jawClenchStatusText.setText(jawClenchStatus);

        });

    }

    /**
     * Helper methods to get different packet values.  These methods simply store the
     * data in the buffers for later display in the UI.
     *
     * getEegChannelValue can be used for any EEG or EEG derived data packet type
     * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
     * of MuseDataPacketType for all of the available values.
     * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
     * getValue methods.
     */
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

    private void getAccelValues(MuseDataPacket p) {
        accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X);
        accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y);
        accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z);
    }

    //TODO our code
    private void getGyroValues(MuseDataPacket p) {
        gyroBuffer[0] = p.getGyroValue(Gyro.X);
        gyroBuffer[1] = p.getGyroValue(Gyro.Y);
        gyroBuffer[2] = p.getGyroValue(Gyro.Z);
    }

    private void getGyroToggleX() {


//        if (gyroBuffer[0] < gyroThresholdX && gyroBuffer[0] > -gyroThresholdX) gyroToggleX = 0;


        if (gyroToggleX == 0 && gyroBuffer[0] > gyroThresholdX) gyroToggleX = 1;
        if (gyroToggleX == 0 && gyroBuffer[0] < -gyroThresholdX) gyroToggleX = -1;
//        if (gyroToggleX == -1 && gyroBuffer[0] > gyroThresholdX) gyroToggleX = 0;
//        if (gyroToggleX == 1 && gyroBuffer[0] < -gyroThresholdX) gyroToggleX = 0;
    }

//    private void GetBlinkFlag(MuseArtifactPacket p) {
//        blinkFlag = p.getHeadbandOn(); //TODO for real change this
//    }


    //--------------------------------------
    // UI Specific methods

    /**
     * Initializes the UI of the example application.
     */
    private void initUI() {
        setContentView(R.layout.activity_main);
        Button refreshButton = findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button pauseButton = findViewById(R.id.pause);
        pauseButton.setOnClickListener(this);

        // This object is the way we show available muse headsets in the ui
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        Spinner musesSpinner = findViewById(R.id.muses_spinner);
        musesSpinner.setAdapter(spinnerAdapter);

        // This is for the available ble devices
        // Button for refreshing devices
        Button bluetoothRefreshButton = findViewById(R.id.refresh_bluetooth);
        bluetoothRefreshButton.setOnClickListener(this);

        // Recycler view for showing devices

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//        deviceAdapter = new DeviceAdapter(this, bleManager.getDeviceList());
        recyclerView.setAdapter(deviceAdapter);

    }

    /**
     * The runnable that is used to update the UI at 60Hz.
     *
     * We update the UI from this Runnable instead of in packet handlers
     * because packets come in at high frequency -- 220Hz or more for raw EEG
     * -- and it only makes sense to update the UI at about 60fps. The update
     * functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Runnable tickUi = new Runnable() {
        @Override
        public void run() {
            if (eegStale) {
                updateEeg();
            }
            if (accelStale) {
                updateAccel();
            }
            if (alphaStale) {
                updateAlpha();
            }

            //TODO our code
            if (gyroStale) {
                updateGyro();
                updateGyroToggleX();
            }

//            if (blinkStale) {
//                updateBlinkFlag();
//            }

            // Start BLE scan if not already scanning
            if (!bleManager.isScanning() && bleManager.shouldScan) {
                bleManager.startScan();
            }

            handler.postDelayed(tickUi, 1000 / 60);
        }
    };

    /**
     * The following methods update the TextViews in the UI with the data
     * from the buffers.
     */
    private void updateAccel() {
        TextView acc_x = findViewById(R.id.acc_x);
        TextView acc_y = findViewById(R.id.acc_y);
        TextView acc_z = findViewById(R.id.acc_z);
        acc_x.setText(String.format(Locale.getDefault(), "%6.2f", accelBuffer[0]));
        acc_y.setText(String.format(Locale.getDefault(), "%6.2f", accelBuffer[1]));
        acc_z.setText(String.format(Locale.getDefault(), "%6.2f", accelBuffer[2]));
    }

    private void updateEeg() {
        TextView tp9 = findViewById(R.id.eeg_tp9);
        TextView fp1 = findViewById(R.id.eeg_af7);
        TextView fp2 = findViewById(R.id.eeg_af8);
        TextView tp10 = findViewById(R.id.eeg_tp10);
        tp9.setText(String.format(Locale.getDefault(), "%6.2f", eegBuffer[0]));
        fp1.setText(String.format(Locale.getDefault(), "%6.2f", eegBuffer[1]));
        fp2.setText(String.format(Locale.getDefault(), "%6.2f", eegBuffer[2]));
        tp10.setText(String.format(Locale.getDefault(), "%6.2f", eegBuffer[3]));
    }

    private void updateAlpha() {
        TextView elem1 = findViewById(R.id.elem1);
        elem1.setText(String.format(Locale.getDefault(), "%6.2f", alphaBuffer[0]));
        TextView elem2 = findViewById(R.id.elem2);
        elem2.setText(String.format(Locale.getDefault(), "%6.2f", alphaBuffer[1]));
        TextView elem3 = findViewById(R.id.elem3);
        elem3.setText(String.format(Locale.getDefault(), "%6.2f", alphaBuffer[2]));
        TextView elem4 = findViewById(R.id.elem4);
        elem4.setText(String.format(Locale.getDefault(), "%6.2f", alphaBuffer[3]));
    }

    //TODO our code
    private void updateGyro() {
        TextView gyro_x = findViewById(R.id.gyro_x);
        TextView gyro_y = findViewById(R.id.gyro_y);
        TextView gyro_z = findViewById(R.id.gyro_z);
        gyro_x.setText(String.format(Locale.getDefault(), "%6.2f", gyroBuffer[0]));
        gyro_y.setText(String.format(Locale.getDefault(), "%6.2f", gyroBuffer[1]));
        gyro_z.setText(String.format(Locale.getDefault(), "%6.2f", gyroBuffer[2]));
    }

    //This is a state machine implementation for the gyro toggle logic.
//    public class HeadTiltStateMachine {
//        private enum State {
//            IDLE,
//            HEAD_TILTED_RIGHT,
//            HEAD_TILTED_LEFT
//        }
//
//        private State currentState = State.IDLE;
//        private double gyroXThreshold = 100; // Adjust this threshold as needed
//
//        public void updateGyroXState(double gyroX) {
//            switch (currentState) {
//                case IDLE:
//                    if (gyroX > gyroXThreshold) {
//                        currentState = State.HEAD_TILTED_RIGHT;
//                        gyroToggleX  = 1;
//                    } else if (gyroX < -gyroXThreshold) {
//                        currentState = State.HEAD_TILTED_LEFT;
//                        gyroToggleX  = -1;
//                    }
//                    break;
//
//                case HEAD_TILTED_RIGHT:
//                    if (gyroX < -gyroXThreshold) {
//                        currentState = State.IDLE;
//                        gyroToggleX  = 0;
//                    }
//                    break;
//
//                case HEAD_TILTED_LEFT:
//                    if (gyroX > gyroXThreshold) {
//                        currentState = State.IDLE;
//                        gyroToggleX  = 0;
//                    }
//                    break;
//            }
//        }
//
//        public State getCurrentState() {
//            return currentState;
//        }
//    }


    private void updateGyroToggleX() {
        TextView gyro_toggle_x = findViewById(R.id.gyro_toggle_x);
        gyro_toggle_x.setText(String.format(Locale.getDefault(), "%d", gyroToggleX));
    }


    //--------------------------------------
    // File I/O

//    /**
//     * We don't want to block the UI thread while we write to a file, so the file
//     * writing is moved to a separate thread.
//     */
//    private final Thread fileThread = new Thread() {
//        @Override
//        public void run() {
//            Looper.prepare();
//            fileHandler.set(new Handler(getMainLooper()));
//            final File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//            final File file = new File(dir, "new_muse_file.muse" );
//            // MuseFileWriter will append to an existing file.
//            // In this case, we want to start fresh so the file
//            // if it exists.
//            if (file.exists() && !file.delete()) {
//                Log.e(TAG, "file not successfully deleted");
//            }
//            Log.i(TAG, "Writing data to: " + file.getAbsolutePath());
//            fileWriter.set(MuseFileFactory.getMuseFileWriter(file));
//            Looper.loop();
//        }
//    };
//
//    /**
//     * Writes the provided MuseDataPacket to the file.  MuseFileWriter knows
//     * how to write all packet types generated from LibMuse.
//     * @param p     The data packet to write.
//     */
//    private void writeDataPacketToFile(final MuseDataPacket p) {
//        Handler h = fileHandler.get();
//        if (h != null) {
//            h.post(() -> fileWriter.get().addDataPacket(0, p));
//        }
//    }
//
//    //TODO our code
//    private void writeArtifactPacketToFile(final MuseArtifactPacket p) {
//        Handler h = fileHandler.get();
//        if (h != null) {
//            h.post(() -> fileWriter.get().addArtifactPacket(0, p));
//        }
//    }
//
//    /**
//     * Flushes all the data to the file and closes the file writer.
//     */
//    private void saveFile() {
//        Handler h = fileHandler.get();
//        if (h != null) {
//            h.post(() -> {
//                MuseFileWriter w = fileWriter.get();
//                // Annotation strings can be added to the file to
//                // give context as to what is happening at that point in
//                // time.  An annotation can be an arbitrary string or
//                // may include additional AnnotationData.
//                w.addAnnotationString(0, "Disconnected");
//                w.flush();
//                w.close();
//            });
//        }
//    }
//
//    /**
//     * Reads the provided .muse file and prints the data to the logcat.
//     * @param name  The name of the file to read.  The file in this example
//     *              is assumed to be in the Environment.DIRECTORY_DOWNLOADS
//     *              directory.
//     */
//    @SuppressWarnings("unused")
//    private void playMuseFile(String name) {
//
//        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//        File file = new File(dir, name);
//
//        final String tag = "Muse File Reader";
//
//        if (!file.exists()) {
//            Log.w(tag, "file doesn't exist");
//            return;
//        }
//
//        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);
//
//        // Loop through each message in the file.  gotoNextMessage will read the next message
//        // and return the result of the read operation as a Result.
//        Result res = fileReader.gotoNextMessage();
//        while (res.getLevel() == ResultLevel.R_INFO && !res.getInfo().contains("EOF")) {
//
//            MessageType type = fileReader.getMessageType();
//            int id = fileReader.getMessageId();
//            long timestamp = fileReader.getMessageTimestamp();
//
//            Log.i(tag, "type: " + type.toString() +
//                  " id: " + id +
//                  " timestamp: " + timestamp);
//
//            switch(type) {
//                // EEG messages contain raw EEG data or DRL/REF data.
//                // EEG derived packets like ALPHA_RELATIVE and artifact packets
//                // are stored as MUSE_ELEMENTS messages.
//                case EEG:
//                case BATTERY:
//                case ACCELEROMETER:
//                case QUANTIZATION:
//                case GYRO:
//                case MUSE_ELEMENTS:
//                    MuseDataPacket packet = fileReader.getDataPacket();
//                    Log.i(tag, "data packet: " + packet.packetType().toString());
//                    break;
//                case VERSION:
//                    MuseVersion version = fileReader.getVersion();
//                    Log.i(tag, "version" + version.getFirmwareType());
//                    break;
//                case CONFIGURATION:
//                    MuseConfiguration config = fileReader.getConfiguration();
//                    Log.i(tag, "config" + config.getBluetoothMac());
//                    break;
//                case ANNOTATION:
//                    AnnotationData annotation = fileReader.getAnnotation();
//                    Log.i(tag, "annotation" + annotation.getData());
//                    break;
//                default:
//                    break;
//            }
//
//            // Read the next message.
//            res = fileReader.gotoNextMessage();
//        }
//    }

    //--------------------------------------
    // Listener translators
    //
    // Each of these classes extend from the appropriate listener and contain a weak reference
    // to the activity.  Each class simply forwards the messages it receives back to the Activity.

    // Muse listeners
    static class MuseL extends MuseListener {
        final WeakReference<MainActivity> activityRef;

        MuseL(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
            activityRef.get().museListChanged();
        }
    }

    static class ConnectionListener extends MuseConnectionListener {
        final WeakReference<MainActivity> activityRef;

        ConnectionListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    static class DataListener extends MuseDataListener {
        final WeakReference<MainActivity> activityRef;

        DataListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }
}
