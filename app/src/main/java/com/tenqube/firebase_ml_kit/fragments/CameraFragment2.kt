/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tenqube.firebase_ml_kit.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCapture.Metadata
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.example.cameraxbasic.utils.ANIMATION_FAST_MILLIS
import com.android.example.cameraxbasic.utils.ANIMATION_SLOW_MILLIS
import com.android.example.cameraxbasic.utils.AutoFitPreviewBuilder
import com.android.example.cameraxbasic.utils.simulateClick
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.tenqube.firebase_ml_kit.KEY_EVENT_ACTION
import com.tenqube.firebase_ml_kit.KEY_EVENT_EXTRA
import com.tenqube.firebase_ml_kit.MainActivity
import com.tenqube.firebase_ml_kit.R
import com.tenqube.firebase_ml_kit.facedetection.FaceContourGraphic
import com.tenqube.firebase_ml_kit.facedetection.YourImageAnalyzer
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import com.tenqube.firebase_ml_kit.facedetection.common.VisionImageProcessor
import com.tenqube.firebase_ml_kit.utils.FaceImageManager
import com.tenqube.firebase_ml_kit.utils.GlideApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment2 : Fragment()/*, CoroutineScope */{

//    private val job = Job()
//    override val coroutineContext: CoroutineContext
//        get() = Dispatchers.Default + job

    private lateinit var container: ConstraintLayout
    private lateinit var viewFinder: TextureView
    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var mainExecutor: Executor

    private var displayId = -1
    private var lensFacing = CameraX.LensFacing.BACK//CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var frameProcessor: VisionImageProcessor? = null
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var bgGraphicOverlay: GraphicOverlay

    private var isProcessing: Boolean = false
    private var capturedImage1: File? = null
//    private var capturedImage2: File? = null
//    private var resultImageView: ImageView? = null
    private var resultImage: Bitmap? = null
    private var resultFile: File? = null
    private var cachedTargetDimens = android.util.Size(0, 0)
    private lateinit var faceImageManager: FaceImageManager
    private lateinit var imageView: ImageView
    private var bitmapForFace : Bitmap? = null



    private fun cleanScreen() {
        graphicOverlay.clear()
        bgGraphicOverlay.clear()
    }


    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val shutter = container
                            .findViewById<ImageButton>(R.id.camera_capture_button)
                    shutter.simulateClick()
                }
            }
        }
    }

    override fun onPause() {
//        imageAnalyzer?.removeAnalyzer()
        super.onPause()
    }

    /** Internal reference of the [DisplayManager] */
    private lateinit var displayManager: DisplayManager

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment2.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                preview?.setTargetRotation(view.display.rotation)
                imageCapture?.setTargetRotation(view.display.rotation)
                imageAnalyzer?.setTargetRotation(view.display.rotation)
            }
        } ?: Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true
        mainExecutor = ContextCompat.getMainExecutor(requireContext())
    }

//    override fun onResume() {
//        super.onResume()
//        // Make sure that all permissions are still present, since user could have removed them
//        //  while the app was on paused state
//        if (!PermissionsFragment.hasPermissions(requireContext())) {
//            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
//                    CameraFragmentDirections.actionCameraToPermissions())
//
//        }
//    }



    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View?  {

        val view = inflater.inflate(R.layout.fragment_camera_2, container, false)
        graphicOverlay = view.findViewById(R.id.fireFaceOverlay)
        bgGraphicOverlay = view.findViewById(R.id.bgFaceOverlay)

//        synchronized(processorLock) {
            cleanScreen()
//            frameProcessor?.let { it.stop() }
//            frameProcessor = FaceContourDetectorProcessor()
//        }
        return view

    }

    private fun setGalleryThumbnail(file: File) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = container.findViewById<ImageButton>(R.id.photo_view_button)

        val capturedThumbnail1 = container.findViewById<ImageButton>(R.id.capture_image_1)
//        val capturedThumbnail2 = container.findViewById<ImageButton>(R.id.capture_image_2)
        var doChangeFaces = false

        capturedImage1 = file


        // Run the operations in the view's thread
        thumbnail.post {

            // Remove thumbnail padding
            thumbnail.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())
            capturedThumbnail1.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())
//            capturedThumbnail2.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                    .load(file)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thumbnail)
            capturedImage1?.let {
                Glide.with(capturedThumbnail1)
                    .load(capturedImage1)
                    .apply(RequestOptions.circleCropTransform())
                    .into(capturedThumbnail1)
            }


//            if (doChangeFaces) {
//                cropImages()
//            }

        }

        setBgImage(file)
    }

    private fun backKey() {
        activity?.supportFragmentManager?.popBackStackImmediate()
    }

    private fun setBgImage(file: File) {
        GlobalScope.launch(Dispatchers.IO) {
            val faceImage = GlideApp.with(imageView).asBitmap().load(file).submit().get()
            resultImage = faceImage
//            a()

            withContext(Dispatchers.Main) {
                faceImageManager.startForBg(faceImage, object : FaceImageManager.FaceImage{
                    override fun bringFaceImage(faceBitmap: Bitmap) {
                        GlideApp.with(imageView).load(faceBitmap).into(imageView)
                    }
                })
            }
        }
    }

    private fun a() {
        resultImage?.let { faceImageManager.startForBg(it, object : FaceImageManager.FaceImage{
            override fun bringFaceImage(faceBitmap: Bitmap) {
                bitmapForFace = faceBitmap
                Glide.with(imageView).load(bitmapForFace).into(imageView)
            }
        })}
    }

    /** Define callback that will be triggered after a photo has been taken and saved to disk */
    private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(
                error: ImageCapture.ImageCaptureError, message: String, exc: Throwable?) {
            Log.e(TAG, "Photo capture failed: $message")
            exc?.printStackTrace()
        }

        override fun onImageSaved(photoFile: File) {
            Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")


            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Update the gallery thumbnail with latest picture taken
                setGalleryThumbnail(photoFile)
            }

            // Implicit broadcasts will be ignored for devices running API
            // level >= 24, so if you only target 24+ you can remove this statement
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                requireActivity().sendBroadcast(
                        Intent(Camera.ACTION_NEW_PICTURE, Uri.fromFile(photoFile)))
            }

            // If the folder selected is an external media directory, this is unnecessary
            // but otherwise other apps will not be able to access our images unless we
            // scan them using [MediaScannerConnection]
            val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(photoFile.extension)
            MediaScannerConnection.scanFile(
                    context, arrayOf(photoFile.absolutePath), arrayOf(mimeType), null)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        viewFinder = container.findViewById(R.id.view_finder)
        imageView = container.findViewById(R.id.image_view)
        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, recompute layout
        displayManager = viewFinder.context
                .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out
        viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            // Build UI controls and bind all camera use cases
            updateCameraUi()
            bindCameraUseCases()
            context?.let { faceImageManager = FaceImageManager(it, Size(viewFinder.display.height, viewFinder.display.width), bgGraphicOverlay) }
            // In the background, load latest photo taken (if any) for gallery thumbnail
            lifecycleScope.launch(Dispatchers.IO) {
                outputDirectory.listFiles { file ->
                    EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
                }?.sorted()?.lastOrNull()?.let { setGalleryThumbnail(it) }
            }
        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")
        // Set up the view finder use case to display camera preview
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            // We request aspect ratio but no resolution to let CameraX optimize our use cases
            setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        // Use the auto-fit preview builder to automatically handle size and orientation changes
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, viewFinder)

        // Set up the capture use case to allow users to take photos
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setCaptureMode(CaptureMode.MIN_LATENCY)
            // We request aspect ratio but no resolution to match preview config but letting
            // CameraX optimize for whatever specific resolution best fits requested capture mode
            setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(Surface.ROTATION_0)
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)

        // Setup image analysis pipeline that computes average pixel luminance in real time
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetResolution(android.util.Size(viewFinder.display.height, viewFinder.display.width))
            // In our analysis, we care more about the latest image than analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        imageAnalyzer = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(mainExecutor,
                YourImageAnalyzer { image: FirebaseVisionImage, inputImage :Image?, buffer: ByteBuffer, rotation: Int ->

//                    processingRunnable.setNextFrame(image, rotation)
//                    runFaceContourDetection(image)
//
                    inputImage?.let { cachedTargetDimens = Size(it.width, it.height) }
                    setcameraInfoForOverlay()

                })
        }

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(
                viewLifecycleOwner, preview, imageCapture, imageAnalyzer)
    }



    private fun setcameraInfoForOverlay() {
        val size: Size = Size(viewFinder.display.width, viewFinder.display.height)
//        val size: Size = Size(cachedTargetDimens.width, cachedTargetDimens.height)
//        Log.i("Size","a $size")
        Log.i("Size","b $size ${size.width}")

        val min = Math.min(size.width, size.height)
        val max = Math.max(size.width, size.height)
        if (isPortraitMode()) { // Swap width and height sizes when in portrait, since it will be rotated by
            // 90 degrees
            graphicOverlay.setCameraInfo(min, max, lensFacing.ordinal, cachedTargetDimens)
        } else {
            graphicOverlay.setCameraInfo(max, min, lensFacing.ordinal, cachedTargetDimens)
        }
    }

    private fun isPortraitMode(): Boolean {
        val orientation = context!!.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true
        }
        Log.d(
            "isPortraitMode",
            "isPortraitMode returning false by default"
        )
        return false
    }

    private fun runFaceContourDetection(firebaseVisionImage: FirebaseVisionImage) {
        try {
            val image = firebaseVisionImage
            val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .build()
            isProcessing = false
            val detector =
                FirebaseVision.getInstance().getVisionFaceDetector(options)
            detector.detectInImage(image)
                .addOnSuccessListener { faces ->
                    isProcessing = true
                    processFaceContourDetectionResult(faces)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    isProcessing = true
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun processFaceContourDetectionResult(faces: List<FirebaseVisionFace>) { // Task completed successfully
        if (faces.isEmpty()) {
            showToast("No face found")
            graphicOverlay.clear()
            return
        }
        graphicOverlay.clear()
//        graphicOverlay.setCameraInfo(viewFinder.display.width, viewFinder.display.height, lensFacing.ordinal)

        for (i in faces.indices) {
            val face = faces[i]
            val faceGraphic = FaceContourGraphic(graphicOverlay, face)
            graphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }


    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }



    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): AspectRatio {
        val previewRatio = max(width, height).toDouble() / min(width, height)

        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes */
    @SuppressLint("RestrictedApi")
    private fun updateCameraUi() {

        // Remove previous UI if any
        container.findViewById<ConstraintLayout>(R.id.camera_ui_container_2)?.let {
            container.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val controls = View.inflate(requireContext(), R.layout.camera_ui_container_2, container)

        controls.findViewById<ImageButton>(R.id.capture_image_1).setOnClickListener {
            capturedImage1?.let { goPictureFragment(it) }

        }

//        imageView = controls.findViewById<ImageView>(R.id.image_view)

//        controls.findViewById<ImageButton>(R.id.capture_image_2).setOnClickListener {
//            capturedImage2?.let { goPictureFragment(it) }
//        }
//
//        controls.findViewById<ImageButton>(R.id.change_button).setOnClickListener {
//
//        }
//
//        resultImageView = controls.findViewById(R.id.preview_image)
//        resultImageView?.setOnClickListener {
//            resultImage?.let {
//                saveBitmapToFile(it)?.let { file -> goPictureFragment(file) }
//            }
//        }


        // Listener for button used to capture photo
        controls.findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener {
            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile =
                    createFile(
                        outputDirectory,
                        FILENAME,
                        PHOTO_EXTENSION
                    )

                // Setup image capture metadata
                val metadata = Metadata().apply {
                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraX.LensFacing.FRONT
                }

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(photoFile, metadata, mainExecutor, imageSavedListener)

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    container.postDelayed({
                        container.foreground = ColorDrawable(Color.WHITE)
                        container.postDelayed(
                                { container.foreground = null }, ANIMATION_FAST_MILLIS)
                    }, ANIMATION_SLOW_MILLIS)
                }
            }
        }

        // Listener for button used to switch cameras
        controls.findViewById<ImageButton>(R.id.camera_switch_button).setOnClickListener {
            lensFacing = if (CameraX.LensFacing.FRONT == lensFacing) {
                CameraX.LensFacing.BACK
            } else {
                CameraX.LensFacing.FRONT
            }
            try {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing)

                // Unbind all use cases and bind them again with the new lens facing configuration
                CameraX.unbindAll()
                bindCameraUseCases()
            } catch (exc: Exception) {
                // Do nothing
            }
        }

        // Listener for button used to view last photo
        controls.findViewById<ImageButton>(R.id.photo_view_button).setOnClickListener {
            goGalleryFragment()
        }

//        setcameraInfoForOverlay()

//        capturedImage1?.let { setBgImage(it) }


//        graphicOverlay.setCameraInfo(viewFinder.display.width, viewFinder.display.height, lensFacing.ordinal)
    }



    private fun saveBitmapToFile(bitmap: Bitmap): File?{

        context?.let {

            val photoFile =
            createFile(
                outputDirectory,
                FILENAME,
                PHOTO_EXTENSION
            )

//            val tempFile : File = File(storage,fileName)

            try {
                photoFile.createNewFile()  // 파일을 생성해주고

                val out = FileOutputStream(photoFile)

                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)

                out.close()

            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            resultFile = photoFile
            return photoFile
        }
        return null
    }

    private fun goGalleryFragment() {
        activity?.let {
            it.supportFragmentManager.beginTransaction()
                .add(R.id.container, GalleryFragment.newInstance(outputDirectory.absolutePath))
                .commitNow()
        }
    }

    private fun goPictureFragment(image: File) {
        activity?.let {
            it.supportFragmentManager.beginTransaction()
                .add(R.id.container, PhotoFragment.create(image))
                .commitNow()
        }
    }



    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)

        fun newInstance() = CameraFragment2()
    }
}
