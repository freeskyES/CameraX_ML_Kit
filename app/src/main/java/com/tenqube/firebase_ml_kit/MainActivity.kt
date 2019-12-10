package com.tenqube.firebase_ml_kit

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Matrix
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.CameraX
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.example.cameraxbasic.utils.FLAGS_FULLSCREEN
import com.tenqube.firebase_ml_kit.fragments.CameraFragment
import com.tenqube.firebase_ml_kit.fragments.CameraFragment2
import com.tenqube.firebase_ml_kit.fragments.GalleryFragment
import com.tenqube.firebase_ml_kit.fragments.ImageFragment
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"
private const val IMMERSIVE_FLAG_TIMEOUT = 500L

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
        if (allPermissionsGranted()) {
//            viewFinder.post { startCamera() }
//            openCamera()

        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }


        findViewById<Button>(R.id.image).setOnClickListener {
            openImage()
        }

        findViewById<Button>(R.id.camera).setOnClickListener {
            openCamera()
        }

        findViewById<Button>(R.id.camera2).setOnClickListener {
            openCamera2()
        }

        findViewById<Button>(R.id.camera3).setOnClickListener {
            openGallery()
        }

//        // Every time the provided texture view changes, recompute layout
//        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
//            updateTransform()
//        }
    }

//    private val executor = Executors.newSingleThreadExecutor()
//    private lateinit var viewFinder: TextureView
//
//    private fun startCamera() {
//        // Create configuration object for the viewfinder use case
//        val previewConfig = PreviewConfig.Builder().apply {
//            setTargetResolution(Size(640, 480))
//        }.build()
//
//
//        // Build the viewfinder use case
//        val preview = Preview(previewConfig)
//
//        // Every time the viewfinder is updated, recompute layout
//        preview.setOnPreviewOutputUpdateListener {
//
//            // To update the SurfaceTexture, we have to remove it and re-add it
//            val parent = viewFinder.parent as ViewGroup
//            parent.removeView(viewFinder)
//            parent.addView(viewFinder, 0)
//
//            viewFinder.surfaceTexture = it.surfaceTexture
//            updateTransform()
//        }
//
//        // Bind use cases to lifecycle
//        // If Android Studio complains about "this" being not a LifecycleOwner
//        // try rebuilding the project or updating the appcompat dependency to
//        // version 1.1.0 or higher.
//        CameraX.bindToLifecycle(this, preview)
//    }
//
//    private fun updateTransform() {
//        val matrix = Matrix()
//
//        // Compute the center of the view finder
//        val centerX = viewFinder.width / 2f
//        val centerY = viewFinder.height / 2f
//
//        // Correct preview output to account for display rotation
//        val rotationDegrees = when(viewFinder.display.rotation) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> return
//        }
//        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
//
//        // Finally, apply transformations to our TextureView
//        viewFinder.setTransform(matrix)
//    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
//                openCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun openCamera() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, CameraFragment.newInstance())
            .commitNow()
    }

    private fun openCamera2() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, CameraFragment2.newInstance())
            .commitNow()
    }

    private fun openGallery() {

        supportFragmentManager.beginTransaction()
            .add(R.id.container, GalleryFragment.newInstance(getOutputDirectory(this).absolutePath))
            .commitNow()
    }

    private fun openImage() {

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ImageFragment.newInstance(getOutputDirectory(this).absolutePath))
            .commitNow()
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
//        container.postDelayed({
//            container.systemUiVisibility = FLAGS_FULLSCREEN
//        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    companion object {

        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val intent = Intent(KEY_EVENT_ACTION).apply { putExtra(KEY_EVENT_EXTRA, keyCode) }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
