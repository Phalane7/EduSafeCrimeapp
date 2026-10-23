package com.example.edusafe.data;

import java.io.Serializable;


public class EmergencyContact implements Serializable {
    public final String name;
    public final String phoneNumber;
    public final int priority;

    public EmergencyContact(String name, String phoneNumber, int priority) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.priority = priority;
    }
}
