package com.choosemuse.example.libmuse;

public class MockBLEDevice {
    private final String name;
    private final String address;

    public MockBLEDevice(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}
