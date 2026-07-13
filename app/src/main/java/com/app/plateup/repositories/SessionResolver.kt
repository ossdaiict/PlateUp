package com.app.plateup.repositories

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Small, authoritative boundary between Firebase Auth and the three existing
 * portals. It deliberately owns only session persistence and role validation;
 * screen navigation remains in the existing activities.
 */
class SessionResolver(context: Context) {

    sealed class Session {
        data class Student(val uid: String) : Session()
        data class Vendor(val uid: String, val canteenId: String) : Session()
        data class Admin(val uid: String) : Session()
        object RegistrationRequired : Session()
        object None : Session()
    }

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val adminPrefs = context.getSharedPreferences("admin_session", Context.MODE_PRIVATE)
    private val vendorPrefs = context.getSharedPreferences("vendor_session", Context.MODE_PRIVATE)

    suspend fun resolveStartup(): Session {
        val user = auth.currentUser ?: return Session.None

        val savedAdminUid = adminPrefs.getString(KEY_AUTH_UID, null)
        if (adminPrefs.getBoolean(KEY_ADMIN_LOGGED_IN, false) && savedAdminUid == user.uid) {
            if (database.child("admin_users").child(user.uid).get().await().exists()) {
                return Session.Admin(user.uid)
            }
            clearAdminSession()
        }

        val savedVendorUid = vendorPrefs.getString(KEY_AUTH_UID, null)
        val savedCanteenId = vendorPrefs.getString(KEY_CANTEEN_ID, null)
        if (vendorPrefs.getBoolean(KEY_VENDOR_LOGGED_IN, false) && savedVendorUid == user.uid && !savedCanteenId.isNullOrBlank()) {
            val ownerCanteenId = database.child("canteen_owners").child(user.uid).get().await().getValue(String::class.java)
            if (ownerCanteenId == savedCanteenId) {
                return Session.Vendor(user.uid, savedCanteenId)
            }
            clearVendorSession()
        }

        if (user.isAnonymous) return Session.None

        return if (database.child("students").child(user.uid).child("name").get().await().exists()) {
            Session.Student(user.uid)
        } else {
            Session.RegistrationRequired
        }
    }

    suspend fun beginAnonymousPortalSession(): FirebaseUser {
        clearPortalSessions()
        auth.signOut()
        return auth.signInAnonymously().await().user
            ?: error("Anonymous authentication did not return a user")
    }

    fun saveVendorSession(uid: String, canteenId: String, canteenName: String) {
        vendorPrefs.edit {
            putBoolean(KEY_VENDOR_LOGGED_IN, true)
            putString(KEY_AUTH_UID, uid)
            putString(KEY_CANTEEN_ID, canteenId)
            putString(KEY_CANTEEN_NAME, canteenName)
        }
    }

    fun saveAdminSession(uid: String) {
        adminPrefs.edit {
            putBoolean(KEY_ADMIN_LOGGED_IN, true)
            putString(KEY_AUTH_UID, uid)
        }
    }

    fun clearVendorSession() {
        vendorPrefs.edit { clear() }
    }

    fun clearAdminSession() {
        adminPrefs.edit { clear() }
    }

    fun clearPortalSessions() {
        clearVendorSession()
        clearAdminSession()
    }

    fun signOutCurrentSession() {
        clearPortalSessions()
        auth.signOut()
    }

    companion object {
        const val KEY_VENDOR_LOGGED_IN = "vendor_logged_in"
        const val KEY_ADMIN_LOGGED_IN = "admin_logged_in"
        const val KEY_AUTH_UID = "auth_uid"
        const val KEY_CANTEEN_ID = "canteen_id"
        const val KEY_CANTEEN_NAME = "canteen_name"
    }
}
