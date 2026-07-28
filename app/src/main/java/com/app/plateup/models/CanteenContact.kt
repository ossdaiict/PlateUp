package com.app.plateup.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CanteenContact(
    val name: String = "",
    val role: String = "",
    val phoneNumber: String = "", // Canonical E.164 format
    var isPrimary: Boolean = false
) : Parcelable
