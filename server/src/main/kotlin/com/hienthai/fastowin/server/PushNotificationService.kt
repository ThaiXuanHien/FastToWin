package com.hienthai.fastowin.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

interface PushNotificationService {
    suspend fun sendNotification(fcmToken: String, title: String, body: String)
}

object NoOpPushNotificationService : PushNotificationService {
    override suspend fun sendNotification(fcmToken: String, title: String, body: String) {
        println("Dummy Push: []  - ")
    }
}

class FirebasePushNotificationService : PushNotificationService {
    init {
        try {
            val serviceAccount = FileInputStream("firebase-adminsdk.json")
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build()
            FirebaseApp.initializeApp(options)
            println("Firebase Admin initialized.")
        } catch (e: Exception) {
            println("Failed to initialize Firebase Admin SDK. Push notifications will be disabled: ")
        }
    }

    override suspend fun sendNotification(fcmToken: String, title: String, body: String) {
        if (FirebaseApp.getApps().isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build()
                FirebaseMessaging.getInstance().send(message)
            } catch (e: Exception) {
                println("Error sending FCM message: ")
            }
        }
    }
}
