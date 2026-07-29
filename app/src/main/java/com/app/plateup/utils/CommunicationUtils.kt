package com.app.plateup.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object CommunicationUtils {
    fun dialNumber(context: Context, phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No dialer application found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch dialer", Toast.LENGTH_SHORT).show()
        }
    }
}
