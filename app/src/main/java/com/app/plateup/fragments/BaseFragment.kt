package com.app.plateup.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.app.plateup.activities.BaseActivity
import com.app.plateup.R
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

abstract class BaseFragment : Fragment() {

    private val listeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private var isStarted = false

    fun showLoading(message: String = "Loading...") {
        (activity as? BaseActivity)?.showLoading(message)
    }

    fun hideLoading() {
        (activity as? BaseActivity)?.hideLoading()
    }

    fun showSuccess(message: String) {
        (activity as? BaseActivity)?.showSuccess(message)
    }

    fun showError(message: String, retryAction: (() -> Unit)? = null) {
        (activity as? BaseActivity)?.showError(message, retryAction)
    }

    fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButton: String = "Confirm",
        negativeButton: String = "Cancel",
        onConfirm: () -> Unit
    ) {
        (activity as? BaseActivity)?.showConfirmationDialog(title, message, positiveButton, negativeButton, onConfirm)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAutomaticSystemInsets(view)
    }

    private fun setupAutomaticSystemInsets(view: View) {
        val backImage = view.findViewById<View>(R.id.backImage)
        val titleText = view.findViewById<View>(R.id.titleText)
        val greetingText = view.findViewById<View>(R.id.greetingText)
        
        backImage?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
        titleText?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
        greetingText?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
    }

    fun View.applySystemInsets(applyTop: Boolean = true, applyBottom: Boolean = true, useMargin: Boolean = false) {
        (activity as? BaseActivity)?.run {
            this@applySystemInsets.applySystemInsets(applyTop, applyBottom, useMargin)
        }
    }

    fun registerListener(query: Query, listener: ValueEventListener) {
        if (!listeners.any { it.first == query && it.second == listener }) {
            listeners.add(query to listener)
            if (isStarted) {
                query.addValueEventListener(listener)
            }
        }
    }

    fun unregisterListener(query: Query, listener: ValueEventListener) {
        listeners.removeAll { it.first == query && it.second == listener }
        query.removeEventListener(listener)
    }

    fun hideKeyboard() {
        (activity as? BaseActivity)?.hideKeyboard()
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        listeners.forEach { (query, listener) ->
            query.addValueEventListener(listener)
        }
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        listeners.forEach { (query, listener) ->
            query.removeEventListener(listener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listeners.clear()
    }
}
