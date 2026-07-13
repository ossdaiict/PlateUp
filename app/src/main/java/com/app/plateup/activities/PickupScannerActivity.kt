package com.app.plateup.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.plateup.databinding.ActivityPickupScannerBinding
import com.app.plateup.models.OrderItem
import com.google.firebase.functions.FirebaseFunctions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PickupScannerActivity : BaseActivity() {

    private lateinit var binding: ActivityPickupScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var functions: FirebaseFunctions
    private lateinit var preferences: SharedPreferences
    private var canteenId = ""
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickupScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        functions = FirebaseFunctions.getInstance()
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)
        canteenId = preferences.getString("canteen_id", "") ?: ""

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.enterCodeBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        binding.backBtn.setOnClickListener { finish() }
        binding.enterCodeBtn.setOnClickListener { showManualCodeDialog() }
        binding.cancelBtn.setOnClickListener { resetScanner() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showError("Camera permission is required to scan QR codes")
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImageProxy(imageProxy)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null) {
                            isProcessing = true
                            validateIdentifier(rawValue)
                            break
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun validateIdentifier(identifier: String) {
        showLoading("Finding order...")
        val data = hashMapOf(
            "identifier" to identifier,
            "canteenId" to canteenId
        )

        functions.getHttpsCallable("validatePickupIdentifier")
            .call(data)
            .addOnSuccessListener { result ->
                hideLoading()
                val res = result.data as Map<*, *>
                showConfirmation(res)
            }
            .addOnFailureListener { e ->
                hideLoading()
                showError(e.message ?: "Validation failed")
                isProcessing = false
            }
    }

    private fun showConfirmation(orderData: Map<*, *>) {
        runOnUiThread {
            binding.confirmationCard.visibility = View.VISIBLE
            binding.confStudentName.text = orderData["studentName"] as String
            binding.confOrderId.text = "Order #${(orderData["orderId"] as String).takeLast(6)}"
            
            val items = orderData["items"] as List<*>
            val itemsSummary = items.joinToString(", ") { 
                val item = it as Map<*, *>
                "${item["quantity"]}x ${item["name"]}"
            }
            binding.confItemsList.text = itemsSummary
            binding.confAmount.text = "Total: ₹${orderData["totalAmount"]}"

            binding.confirmBtn.setOnClickListener {
                confirmOrderPickup(orderData["orderId"] as String)
            }
        }
    }

    private fun confirmOrderPickup(orderId: String) {
        showLoading("Confirming pickup...")
        val data = hashMapOf(
            "orderId" to orderId,
            "canteenId" to canteenId
        )

        functions.getHttpsCallable("confirmPickup")
            .call(data)
            .addOnSuccessListener {
                hideLoading()
                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                showSuccess("Pickup confirmed successfully!")
                finish()
            }
            .addOnFailureListener { e ->
                hideLoading()
                showError(e.message ?: "Confirmation failed")
            }
    }

    private fun showManualCodeDialog() {
        val editText = EditText(this)
        editText.hint = "Enter 6-digit code"
        
        AlertDialog.Builder(this)
            .setTitle("Manual Pickup")
            .setView(editText)
            .setPositiveButton("Verify") { _, _ ->
                val code = editText.text.toString().trim()
                if (code.length == 6) {
                    isProcessing = true
                    validateIdentifier(code)
                } else {
                    showError("Please enter a valid 6-digit code")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetScanner() {
        binding.confirmationCard.visibility = View.GONE
        isProcessing = false
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    companion object {
        private const val TAG = "PickupScanner"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
