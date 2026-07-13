package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.NotificationsAdapter
import com.app.plateup.databinding.ActivityStudentNotificationsBinding
import com.app.plateup.models.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StudentNotificationsActivity : BaseActivity() {

    private lateinit var binding: ActivityStudentNotificationsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var notificationsList: ArrayList<Notification>
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        notificationsList = ArrayList()
        adapter = NotificationsAdapter(
            this,
            notificationsList,
            onNotificationClick = { notification ->
                markNotificationRead(notification)
            }
        )

        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecyclerView.adapter = adapter
        binding.notificationsRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        binding.backImage.setOnClickListener { finish() }

        loadNotifications()

    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return
        val notifRef = database.child("notifications").child(userId)
        val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notificationsList.clear()
                    for (child in snapshot.children) {
                        val notification = child.getValue(Notification::class.java)
                        if (notification != null) {
                            notificationsList.add(notification)
                        }
                    }

                    notificationsList.sortByDescending { it.timestamp }

                    binding.emptyStateText.visibility =
                        if (notificationsList.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    showError(error.message)
                }
        }
        registerListener(notifRef, listener)
    }

    private fun markNotificationRead(notification: Notification) {
        database.child("notifications")
            .child(auth.currentUser!!.uid)
            .child(notification.notificationId)
            .child("read")
            .setValue(true)

        val intent = Intent(this, StudentOrderDetailsActivity::class.java)
        intent.putExtra("orderId", notification.orderId)
        startActivity(intent)
    }

}
