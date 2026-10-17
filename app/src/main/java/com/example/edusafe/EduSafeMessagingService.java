package com.example.edusafe;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EduSafeMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        Session.putString(this, "fcm_token", token);
        PushRegistrar.send(this, token);
    }

    // Called when a notification arrives while the app is open
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        RemoteMessage.Notification n = message.getNotification();
        if (n == null) return;
        Notifier.show(this, n.getTitle(), n.getBody());
    }
}