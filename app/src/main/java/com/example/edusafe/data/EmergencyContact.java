package com.example.edusafe.data;

import java.io.Serializable;

/** A single emergency contact: who gets alerted, in what order, and on what number. */
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
