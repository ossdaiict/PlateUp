package com.app.plateup.activities

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.app.plateup.R
import com.app.plateup.utils.NetworkMonitor
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

abstract class BaseActivity : AppCompatActivity() {

    private var loadingOverlay: View? = null
    private var loadingMessageTextView: TextView? = null
    private var loadingBackPressCallback: OnBackPressedCallback? = null
    private val listeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private lateinit var networkMonitor: NetworkMonitor
    private var offlineSnackbar: Snackbar? = null

    private var isStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        networkMonitor = NetworkMonitor(this)
        observeConnectivity()
        
        loadingBackPressCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Do nothing, block back button while loading
            }
        }
        onBackPressedDispatcher.addCallback(this, loadingBackPressCallback!!)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val rootView = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        rootView?.let { setupAutomaticSystemInsets(it) }
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        view?.let { setupAutomaticSystemInsets(it) }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        view?.let { setupAutomaticSystemInsets(it) }
    }

    private fun setupAutomaticSystemInsets(rootView: View) {
        val backImage = rootView.findViewById<View>(R.id.backImage)
        val titleText = rootView.findViewById<View>(R.id.titleText)
        val greetingText = rootView.findViewById<View>(R.id.greetingText)
        
        backImage?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
        titleText?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
        greetingText?.applySystemInsets(applyTop = true, applyBottom = false, useMargin = true)
    }

    private fun observeConnectivity() {
        networkMonitor.isOnline.onEach { isOnline ->
            if (!isOnline) {
                showOfflineUI()
            } else {
                hideOfflineUI()
            }
        }.launchIn(lifecycleScope)
    }

    private fun showOfflineUI() {
        if (offlineSnackbar == null) {
            val rootView = findViewById<View>(android.R.id.content)
            offlineSnackbar = Snackbar.make(rootView, "No internet connection", Snackbar.LENGTH_INDEFINITE)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
                .setTextColor(Color.WHITE)
        }
        offlineSnackbar?.show()
    }

    private fun hideOfflineUI() {
        offlineSnackbar?.dismiss()
        // Could show a brief "Back online" snackbar here if desired
    }

    fun showLoading(message: String = "Loading...") {
        if (isFinishing) return

        if (loadingOverlay == null) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            loadingOverlay = layoutInflater.inflate(R.layout.layout_loading_overlay, rootView, false)
            rootView.addView(loadingOverlay)
            loadingMessageTextView = loadingOverlay?.findViewById(R.id.loadingMessageText)
        }

        loadingMessageTextView?.text = message
        loadingBackPressCallback?.isEnabled = true

        loadingOverlay?.let {
            if (it.visibility != View.VISIBLE) {
                it.visibility = View.VISIBLE
                it.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setListener(null)
            }
        }
    }

    fun hideLoading() {
        loadingBackPressCallback?.isEnabled = false
        loadingOverlay?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                loadingOverlay?.visibility = View.GONE
            }
    }

    fun showSuccess(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.success))
            .setTextColor(Color.WHITE)
            .show()
    }

    fun showError(message: String, retryAction: (() -> Unit)? = null) {
        val rootView = findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(rootView, message, if (retryAction != null) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(Color.WHITE)
        
        retryAction?.let {
            snackbar.setAction("Retry") { it() }
            snackbar.setActionTextColor(Color.WHITE)
        }
        snackbar.show()
    }

    fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButton: String = "Confirm",
        negativeButton: String = "Cancel",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { _, _ -> onConfirm() }
            .setNegativeButton(negativeButton, null)
            .show()
    }

    fun View.applySystemInsets(applyTop: Boolean = true, applyBottom: Boolean = true, useMargin: Boolean = false) {
        val initialLeft = this.paddingLeft
        val initialTop = this.paddingTop
        val initialRight = this.paddingRight
        val initialBottom = this.paddingBottom
        
        val params = this.layoutParams as? ViewGroup.MarginLayoutParams
        val initialTopMargin = params?.topMargin ?: 0
        val initialBottomMargin = params?.bottomMargin ?: 0
        
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topInset = if (applyTop) systemBars.top else 0
            val bottomInset = if (applyBottom) systemBars.bottom else 0
            
            if (useMargin && params != null) {
                params.topMargin = initialTopMargin + topInset
                params.bottomMargin = initialBottomMargin + bottomInset
                view.layoutParams = params
            } else {
                view.setPadding(
                    initialLeft,
                    initialTop + topInset,
                    initialRight,
                    initialBottom + bottomInset
                )
            }
            insets
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
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
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
