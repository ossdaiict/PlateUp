package com.app.plateup.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.plateup.R
import com.app.plateup.activities.StudentOrderDetailsActivity
import com.app.plateup.activities.VendorOrderDetailsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PlateUpMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val database = FirebaseDatabase.getInstance().reference

        if (uid != null) {
            database.child("students")
                .child(uid)
                .child("fcmToken")
                .setValue(token)
        }

        val vendorPrefs = getSharedPreferences("vendor_session", MODE_PRIVATE)
        val canteenId = vendorPrefs.getString("canteen_id", "") ?: ""
        if (vendorPrefs.getBoolean("vendor_logged_in", false) && canteenId.isNotEmpty()) {
            database.child("vendor_credentials")
                .child(canteenId)
                .child("fcmTokens")
                .child(token.toDatabaseKey())
                .setValue(token)
        }

    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"] ?: "PlateUp"
        val body = message.data["body"] ?: ""
        val orderId = message.data["orderId"] ?: ""
        val type = message.data["type"]
        val notificationId = message.data["notificationId"] ?: ""

        showNotification(title, body, orderId, notificationId, type)

    }

    private fun showNotification(
        title: String,
        message: String,
        orderId: String,
        notificationId: String,
        type: String?
    ) {
        val channelId = "plateup_notifications"

        val notificationManager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PlateUp Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )

            notificationManager.createNotificationChannel(channel)
        }

        val destination =
            if (type == "NEW_ORDER" || type == "PAYMENT_RECEIVED") {
                VendorOrderDetailsActivity::class.java
            } else {
                StudentOrderDetailsActivity::class.java
            }

        val intent = Intent(this, destination)
        intent.putExtra("orderId", orderId)
        intent.putExtra("notificationId", notificationId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val requestCode = orderId.hashCode()

        val pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .build()

        notificationManager.notify(orderId.hashCode(), notification)

    }

    private fun String.toDatabaseKey(): String {
        return Base64.encodeToString(toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

}
