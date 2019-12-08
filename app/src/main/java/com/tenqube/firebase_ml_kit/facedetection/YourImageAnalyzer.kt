package com.tenqube.firebase_ml_kit.facedetection

import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.tenqube.firebase_ml_kit.facedetection.common.VisionUtils
import com.tenqube.firebase_ml_kit.fragments.LumaListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

typealias YourImageListener = (image: FirebaseVisionImage,
                               inputImage: Image?,
                               data: ByteBuffer,
                               imageRotation: Int) -> Unit


class YourImageAnalyzer(listener: YourImageListener? = null) : ImageAnalysis.Analyzer{

    private val listeners = ArrayList<YourImageListener>().apply { listener?.let { add(it) } }

    private val job = Job()
//    override val coroutineContext: CoroutineContext
//        get() = Dispatchers.Default + job

    private val frameRateWindow = 8
    private val frameTimestamps = ArrayDeque<Long>(5)
    private var lastAnalyzedTimestamp = 0L
    var framesPerSecond: Double = -1.0
        private set

    /**
     * Used to add listeners that will be called with each luma computed
     */
    fun onFrameAnalyzed(listener: YourImageListener) = listeners.add(listener)

    private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
    }

    override fun analyze(imageProxy: ImageProxy?, degrees: Int) {

//        lifecycleScope.launch(Dispatchers.Default + job) {



            // Keep track of frames analyzed
//            val currentTime = System.currentTimeMillis()
//            frameTimestamps.push(currentTime)
//
//            // Compute the FPS using a moving average
//            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
//            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
//            val timestampLast = frameTimestamps.peekLast() ?: currentTime
//            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
//                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0
//
//            // Calculate the average luma no more often than every second
//            if (frameTimestamps.first - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
//                lastAnalyzedTimestamp = frameTimestamps.first

                val mediaImage = imageProxy?.image
                val imageRotation = degreesToFirebaseRotation(degrees)

                if (mediaImage != null) {
                    val image = FirebaseVisionImage.fromMediaImage(mediaImage, imageRotation)
                    // Pass image to an ML Kit Vision API
                    // ...

                // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance
                //  plane
                val byteBuffer = imageProxy.planes[0].buffer

                // Extract image data from callback object
//                val byteArray = byteBuffer.toByteArray()

//                // Convert the data into an array of pixel values ranging 0-255
//                val pixels = data.map { it.toInt() and 0xFF }
//
//                // Compute average luminance for the image
//                val luma = pixels.average()
//
//                // Call all listeners with new value
//                listeners.forEach { it(luma) }

                // Call all listeners with new value
                listeners.forEach {
                    Log.i("analyze","$image $byteBuffer $imageRotation")
                    it(image, mediaImage, byteBuffer, imageRotation)
                }
//            }


//            }

        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }


}