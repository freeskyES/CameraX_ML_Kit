package com.tenqube.firebase_ml_kit

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.tenqube.firebase_ml_kit.facedetection.common.BitmapUtils
import com.tenqube.firebase_ml_kit.facedetection.common.FrameMetadata
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import com.tenqube.firebase_ml_kit.facedetection.common.VisionImageProcessor
import java.nio.ByteBuffer

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement {@link
 * #onSuccess(T, FrameMetadata, GraphicOverlay)} to define what they want to with the detection
 * results and {@link #detectInImage(FirebaseVisionImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
abstract class VisionProcessorBase<T> :
    VisionImageProcessor {

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private var latestImage: ByteBuffer? = null

    @GuardedBy("this")
    private var latestImageMetaData: FrameMetadata? = null

    // To keep the images and metadata in process.
    @GuardedBy("this")
    private var processingImage: ByteBuffer? = null

    @GuardedBy("this")
    private var processingMetaData: FrameMetadata? = null

    @Synchronized
    override fun process(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        latestImage = data
        latestImageMetaData = frameMetadata
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay)
        }
    }

    // Bitmap version
    @Synchronized
    override fun process(bitmap: Bitmap,
                         frameMetadata: FrameMetadata,
                         graphicOverlay: GraphicOverlay) {

        detectInVisionImage(
            //bitmap,//null, /* bitmap */
            FirebaseVisionImage.fromBitmap(bitmap),
            frameMetadata,
            graphicOverlay
        )
    }

    @Synchronized
    override fun process(/*bitmap: Bitmap,*/
                         firebaseVisionImage: FirebaseVisionImage,
                         frameMetadata: FrameMetadata,
                         graphicOverlay: GraphicOverlay) {

        detectInVisionImage(
            /*firebaseVisionImage.bitmap,*///null, /* bitmap */
            firebaseVisionImage,
            frameMetadata,
            graphicOverlay
        )
    }



    @Synchronized
    private fun processLatestImage(graphicOverlay: GraphicOverlay) {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (processingImage != null && processingMetaData != null) {
            processImage(processingImage!!, processingMetaData!!, graphicOverlay)
        }
    }

    private fun processImage(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        val metadata = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setWidth(frameMetadata.width)
            .setHeight(frameMetadata.height)
            .setRotation(frameMetadata.rotation)
            .build()

//        val bitmap = BitmapUtils.getBitmap(data, frameMetadata)
//        Log.i("processImage","bitmap $bitmap")
        detectInVisionImage(
            /*bitmap,*/ FirebaseVisionImage.fromByteBuffer(data, metadata), frameMetadata,
            graphicOverlay
        )
    }

    private fun detectInVisionImage(
        /*originalCameraImage: Bitmap?,*/
        image: FirebaseVisionImage,
        metadata: FrameMetadata?,
        graphicOverlay: GraphicOverlay
    ) {
        detectInImage(image)
            .addOnSuccessListener { results ->
                onSuccess(
                    image.bitmap/*originalCameraImage*/, results,
                    metadata!!,
                    graphicOverlay
                )
                processLatestImage(graphicOverlay)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    override fun stop() {}

    protected abstract fun detectInImage(image: FirebaseVisionImage): Task<T>

    /**
     * Callback that executes with a successful detection result.
     *
     * @param originalCameraImage hold the original image from camera, used to draw the background
     * image.
     */
    protected abstract fun onSuccess(
        originalCameraImage: Bitmap?,
        results: T,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    )

    protected abstract fun onFailure(e: Exception)
}
