package com.tenqube.firebase_ml_kit.facedetection

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.tenqube.firebase_ml_kit.VisionProcessorBase
import com.tenqube.firebase_ml_kit.facedetection.common.CameraImageGraphic
import com.tenqube.firebase_ml_kit.facedetection.common.FrameMetadata
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import java.io.IOException

/**
 * Face Contour Demo.
 */
class FaceContourDetectorProcessor : VisionProcessorBase<List<FirebaseVisionFace>>() {

    private val detector: FirebaseVisionFaceDetector

    init {
        val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .build()

        detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Face Contour Detector: $e")
        }
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionFace>> {
        return detector.detectInImage(image)
    }

    override fun onSuccess(
        originalCameraImage: Bitmap?,
        results: List<FirebaseVisionFace>,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {

        if (results.isEmpty()) {
//            showToast("No face found")
            return
        }
        graphicOverlay.clear()

//        originalCameraImage?.let {
//            val imageGraphic =
//                CameraImageGraphic(
//                    graphicOverlay,
//                    it
//                )
//            graphicOverlay.add(imageGraphic)
//        }

        results.forEach {
            val faceGraphic = FaceContourGraphic(graphicOverlay, it)
            graphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(it)
        }


    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {
        private const val TAG = "FaceContourDetectorProc"
    }

    override fun process(bitmap: Bitmap?, graphicOverlay: GraphicOverlay?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}