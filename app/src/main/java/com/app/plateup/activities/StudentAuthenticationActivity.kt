package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.app.plateup.R
import com.app.plateup.databinding.ActivityStudentAuthenticationBinding
import com.app.plateup.models.Student
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import com.google.firebase.messaging.FirebaseMessaging

class StudentAuthenticationActivity : BaseActivity() {

    private lateinit var binding: ActivityStudentAuthenticationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.googleSignInBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        binding.backImage.setOnClickListener { finish() }

        val credentialManager = CredentialManager.create(this)

        binding.googleSignInBtn.setOnClickListener {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            lifecycleScope.launch {
                showLoading("Signing in with Google...")
                try {
                    val result = credentialManager.getCredential(
                        context =  this@StudentAuthenticationActivity,
                        request = request
                    )

                    val credential = result.credential

                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val googleIdToken = googleIdTokenCredential.idToken

                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        val authResult = auth.signInWithCredential(firebaseCredential).await()

                        val user = authResult.user
                        val email = user?.email ?: ""
                        if (!email.endsWith("dau.ac.in")) {
                            auth.signOut()
                            hideLoading()
                            showError("Only college accounts are allowed")
                            return@launch
                        }

                        val uid = user?.uid!!
                        database.child("students").child(uid).child("name").get()
                            .addOnSuccessListener { snapshot ->
                                hideLoading()
                                if (snapshot.exists() && snapshot.value != null) {
                                    // Registered user: Save token and go to Dashboard
                                    val studentName = snapshot.value.toString()
                                    saveFcmTokenAndFinish(uid, "Welcome back, $studentName!")
                                } else {
                                    // New user: Go to Register screen (credentials are already in Firebase Auth)
                                    val intent = Intent(this@StudentAuthenticationActivity, RegisterActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            }
                            .addOnFailureListener { e ->
                                hideLoading()
                                auth.signOut()
                                showError("Database sync failed: ${e.message}")
                            }

                    }
                } catch (_: GetCredentialCancellationException) {
                    hideLoading()
                } catch (e: Exception) {
                    hideLoading()
                    showError(e.message ?: "Unknown error occurred")
                }
            }
        }

    }

    private fun saveFcmTokenAndFinish(uid: String, message: String, isNewUser: Boolean = false) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                database.child("students/$uid/fcmToken").setValue(task.result)
            }

            val intent = if (isNewUser) {
                Intent(this, RegisterActivity::class.java)
            } else {
                Intent(this, StudentDashboardActivity::class.java).apply {
                    putExtra("WELCOME_MESSAGE", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            startActivity(intent)
            finish()
        }
    }

}
