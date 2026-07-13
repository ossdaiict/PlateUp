package com.app.plateup.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.app.plateup.activities.WelcomeActivity
import com.app.plateup.databinding.FragmentProfileBinding
import com.app.plateup.models.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        loadStudentData()

        binding.logoutBtn.setOnClickListener {
            showConfirmationDialog(
                title = "Sign Out",
                message = "Are you sure you want to sign out?",
                positiveButton = "Sign Out",
                onConfirm = { logoutUser() }
            )
        }
    }

    private fun loadStudentData() {
        val uid = auth.currentUser?.uid ?: return
        database.child("students").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val student = snapshot.getValue(Student::class.java)
                if (student != null) {
                    binding.nameText.text = student.name
                    binding.emailText.text = student.email
                    binding.phoneText.text = student.phoneNumber
                    
                    // Extract Student ID: strip "@dau.ac.in"
                    val studentId = student.email.substringBefore("@")
                    binding.studentIdText.text = studentId
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        })
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(requireContext(), WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}