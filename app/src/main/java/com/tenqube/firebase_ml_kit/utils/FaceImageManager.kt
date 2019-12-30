package com.tenqube.firebase_ml_kit.utils

import android.content.Context
import android.graphics.*
import android.util.Pair
import android.util.Size
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.tenqube.firebase_ml_kit.R
import com.tenqube.firebase_ml_kit.facedetection.FaceContourGraphic
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import java.util.ArrayList

class FaceImageManager(val context: Context,
                       private val previewSize: Size,
                       private val graphicOverlay: GraphicOverlay) {

    private var originBitmap: Bitmap? = null

    var resultBitmap : Bitmap? = null
//    private var myFaceInfo: FaceContourGraphic.FaceDetectInfo? = null


    fun startForFace(bitmap: Bitmap, faceImage: FaceImage) {

        val resizeBitmap = resizeImage(bitmap)
        originBitmap = resizeBitmap
        getVisionPoints(resizeBitmap, object : CallbackGraphic {
            override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                   faceInfo: FaceContourGraphic.FaceDetectInfo) {
//                myFaceInfo = faceInfo
                getFaceBitmap(points, faceInfo)?.run { faceImage.bringFaceImage(this) }
            }
        })

    }

    fun startForBg(bitmap: Bitmap, faceImage: FaceImage) {

        val resizeBitmap = resizeImage(bitmap)
        originBitmap = resizeBitmap

        getVisionPoints(resizeBitmap, object : CallbackGraphic {
            override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                   faceInfo: FaceContourGraphic.FaceDetectInfo) {
//                bgFaceInfo = faceInfo
                val result = getBgBitmap(points)

                result?.let {
                    faceImage.bringFaceImage(it)
                }

            }
        })

    }

    interface FaceImage{
        fun bringFaceImage(faceBitmap: Bitmap)
    }

    private fun getVisionPoints(bitmap: Bitmap, callback: CallbackGraphic) {

        val visionImage = FirebaseVisionImage.fromBitmap(bitmap)

        runFaceContourDetectionForImage(visionImage, object :Callback {

            override fun getVisionFaces(faces: List<FirebaseVisionFace>) {
                processFaceContourDetectionResultForImage(faces, object :CallbackGraphic {
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                           faceInfo: FaceContourGraphic.FaceDetectInfo) {
                        callback.getPoints(points, faceInfo)
                    }
                })
            }
        })
    }



    private fun resizeImage(selectBitmap: Bitmap): Bitmap {

        val targetedSize: Pair<Int, Int> = Pair(previewSize.width, previewSize.height)
        val targetWidth = targetedSize.first
        val maxHeight = targetedSize.second
        // Determine how much to scale down the image
        val scaleFactor = Math.max(
            selectBitmap.width.toFloat() / targetWidth.toFloat(),
            selectBitmap.height.toFloat() / maxHeight.toFloat()
        )
        val resizedBitmap = Bitmap.createScaledBitmap(
            selectBitmap,
            (selectBitmap.width / scaleFactor).toInt(),
            (selectBitmap.height / scaleFactor).toInt(),
            true
        )
//        Glide.with(imageView).load(resizedBitmap).into(imageView)
        return resizedBitmap
    }

    private fun runFaceContourDetectionForImage(firebaseVisionImage: FirebaseVisionImage, callback: Callback) {
        try {
            val image = firebaseVisionImage
            val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .build()
            val detector =
                FirebaseVision.getInstance().getVisionFaceDetector(options)
            detector.detectInImage(image)
                .addOnSuccessListener { faces ->
                    callback.getVisionFaces(faces)
//                    processFaceContourDetectionResultForImage(faces, firebaseVisionImage)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
//                    Toast.makeText(context, "fail", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface Callback {
        fun getVisionFaces(faces: List<FirebaseVisionFace>)
    }

    interface CallbackGraphic {
        fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                      faceInfo: FaceContourGraphic.FaceDetectInfo)
    }

    private fun processFaceContourDetectionResultForImage(faces: List<FirebaseVisionFace>, callback: CallbackGraphic) { // Task completed successfully
        if (faces.isEmpty()) {
//            Toast.makeText(context, "not found", Toast.LENGTH_SHORT).show()
            graphicOverlay.clear()
            return
        }
        graphicOverlay.clear()
        for (i in faces.indices) {
            val face = faces[i]

            val faceGraphic = FaceContourGraphic(graphicOverlay, face) { points, faceInfo ->
                //                originBitmapForBg = firebaseVisionImage.bitmap
//                getBgBitmap(it)
                callback.getPoints(points, faceInfo)
            }

            graphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }

    private fun getFaceBitmap(points: ArrayList<FaceContourGraphic.FaceContourData>, myFaceInfo: FaceContourGraphic.FaceDetectInfo): Bitmap? {

        originBitmap?.let { origin ->

            val resultingImage = Bitmap.createBitmap(origin.width,
                origin.height, Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(resultingImage)
            canvas.drawARGB(0, 0, 0, 0)

            val paint = Paint()
            paint.isAntiAlias = true

            val rect = Rect(0, 0, origin.width, origin.height)

            context?.let {
                val color = ContextCompat.getColor(it, R.color.grey)
                paint.color = color
            }

            val path = Path()

            points.forEach {
                path.lineTo(it.px, it.py)
//                canvas.drawCircle(it.px, it.py, 6.0f, Paint(Color.BLUE))
            }

            canvas.drawPath(path, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(origin/*bitmap2*/, rect, rect, paint)


            // 얼굴크기로 bitmap resize 하기
//            myFaceInfo?.run {
                val result = Bitmap.createBitmap(resultingImage, myFaceInfo.left.toInt(), myFaceInfo.top.toInt(), myFaceInfo.rectWidth.toInt(), myFaceInfo.rectHeight.toInt())
                resultBitmap = result
                graphicOverlay.clear()
                return resultBitmap
                //TODO 그리기
//                Glide.with(imageView).load(faceBitmap).into(imageView)
//            }

//            faceBitmap = resultingImage
//            Glide.with(imageView).load(resultingImage).into(imageView)

        }
        return null
    }

    private fun getBgBitmap(points: ArrayList<FaceContourGraphic.FaceContourData>): Bitmap? {

        originBitmap?.let {bitmap ->

            val origin = bitmap//firebaseVisionImage.bitmap//BitmapFactory.decodeResource(getResources(),R.drawable.gallery_12);

//            val resultingImage = origin.copy(origin.config, true)  // 얼굴 외 의 사진 얻을경우 (원본 복사)
            val resultingImage = Bitmap.createBitmap(origin.width,
                origin.height, Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(resultingImage)
            canvas.drawARGB(0, 0, 0, 0)

            val paint = Paint()
            paint.isAntiAlias = true

            val rect = Rect(0, 0, origin.width, origin.height)

            context?.let {
                val color = ContextCompat.getColor(it, R.color.grey)
                paint.color = color
            }

            val path = Path()

            points.forEach {
                path.lineTo(it.px, it.py)
            }
            canvas.drawPath(path, paint)
//            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OUT))

            canvas.drawBitmap(origin/*bitmap2*/, rect, rect, paint)

            resultBitmap = resultingImage
            graphicOverlay.clear()
            return resultBitmap
//            Glide.with(imageView).load(resultingImage).into(imageView)
        }
        return null
    }

    fun bringResultBitmap(): Bitmap? {
        return resultBitmap
    }

}