package com.tenqube.firebase_ml_kit.utils

import android.content.Context
import android.graphics.*
import android.util.Log
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
import kotlin.math.floor
import kotlin.math.round

class FaceImageManager(val context: Context,
                       private val previewSize: Size,
                       private val graphicOverlay: GraphicOverlay) {

//    private var originBitmap: Bitmap? = null
//    var resultBitmap : Bitmap? = null

    private var originBitmapForFace : Bitmap? = null
    var faceBitmap : Bitmap? = null

    private var originBitmapForBg : Bitmap? = null
    var bgBitmap : Bitmap? = null

    private var finalImageBitmap: Bitmap?= null

    var bgFaceInfo: FaceContourGraphic.FaceDetectInfo? = null
    var myFaceInfo: FaceContourGraphic.FaceDetectInfo? = null


    fun startForFace(firebaseVisionImage: FirebaseVisionImage, faceImage: FaceImage) {

//        originBitmapForFace?.let { if (!it.isRecycled) it.recycle() }
//        faceBitmap?.let { if (!it.isRecycled) it.recycle() }

//        val resizeBitmap = resizeImage(bitmap)
        originBitmapForFace = firebaseVisionImage.bitmap
        getVisionPoints(firebaseVisionImage, object : CallbackGraphic {
            override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                   faceInfo: FaceContourGraphic.FaceDetectInfo) {
                myFaceInfo = faceInfo
                getFaceBitmap(points, faceInfo)?.run {
                    faceImage.bringResultImage(this)
                }
            }
        })

    }

    fun startForBg(bitmap: Bitmap, faceImage: FaceImage) {

//        originBitmapForBg?.let { if (!it.isRecycled) it.recycle() }
//        bgBitmap?.let { if (!it.isRecycled) it.recycle() }

        val resizeBitmap = resizeImage(bitmap)
        originBitmapForBg = resizeBitmap

        getVisionPoints(FirebaseVisionImage.fromBitmap(resizeBitmap), object : CallbackGraphic {
            override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                   faceInfo: FaceContourGraphic.FaceDetectInfo) {
                bgFaceInfo = faceInfo
                val result = getBgBitmap(points)

                result?.let {
                    faceImage.bringResultImage(it)
                }
            }
        })
    }

    fun changeFaces(faceImage: FaceImage) {
        bgBitmap?.let { bg -> faceBitmap?.let { face -> bgFaceInfo?.let { bgFaceInfo -> {
//            finalImageBitmap?.let { if (!it.isRecycled) it.recycle() }
            changeFace(bg, face, bgFaceInfo)?.let { faceImage.bringResultImage(it) }

        } } } }

    }

    private fun changeFace(bgBitmap: Bitmap, faceBitmap: Bitmap, bgFaceInfo: FaceContourGraphic.FaceDetectInfo/*, myFaceInfo: FaceContourGraphic.FaceDetectInfo*/): Bitmap? {
        graphicOverlay.clear()

        val resultImage = bgBitmap.copy(bgBitmap.config, true)
        Log.i("changeFace","bgBitmap : ${bgBitmap.width} ${bgBitmap.height}")
        Log.i("changeFace","bgface : $bgFaceInfo")

        Log.i("changeFace","faceBitmap : ${faceBitmap.width} ${faceBitmap.height}")
        // 얼굴 bg 사이즈에 맞게 resize
//        val resizeFace = Bitmap.createScaledBitmap(faceBitmap, bgFaceInfo.rectWidth.toInt(), bgFaceInfo.rectHeight.toInt(), false)
        val resizeFace = Bitmap.createScaledBitmap(faceBitmap, (bgFaceInfo.rectWidth*1.02).toInt(), (bgFaceInfo.rectHeight*1.02).toInt(), false) // 늘어날경우엔 true 로 해줘야 안깨진다
        Log.i("changeFace","resize faceBitmap : ${resizeFace.width} ${resizeFace.height}")


        val canvas = Canvas(resultImage)
        val top = if (bgFaceInfo.top >= 16) bgFaceInfo.top - 16 else bgFaceInfo.top
        val left = if (bgFaceInfo.left >= 2) bgFaceInfo.left - 2 else bgFaceInfo.left
        canvas.drawBitmap(resizeFace, left, top, null)
        finalImageBitmap = resultImage

        if (!resultImage.isRecycled) resultImage.recycle()

        return finalImageBitmap
//        Glide.with(imageView).load(finalImageBitmap).into(imageView)
    }

    interface FaceImage{
        fun bringResultImage(resultBitmap: Bitmap)
    }

    private fun getVisionPoints(firebaseVisionImage: FirebaseVisionImage, callback: CallbackGraphic) {

        val visionImage = firebaseVisionImage//FirebaseVisionImage.fromBitmap(bitmap)

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

        originBitmapForFace?.let { origin ->

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


            // resultingImage 얼굴은 잘 짤리지만 bitmap createBitmap resize 가 안됨

            // 얼굴크기로 bitmap resize 하기
//            myFaceInfo?.run {
            if (myFaceInfo.top > 0 && myFaceInfo.left > 0) {

                Log.i("log","resultingImage : $resultingImage $myFaceInfo ")
                val result = Bitmap.createBitmap(resultingImage, (myFaceInfo.left).toInt(), floor(myFaceInfo.top).toInt(), (myFaceInfo.rectWidth).toInt(), floor(myFaceInfo.rectHeight).toInt())

//            if (!origin.isRecycled) origin.recycle()
//            if (!resultingImage.isRecycled) resultingImage.recycle()

                faceBitmap = result
                graphicOverlay.clear()
                return faceBitmap
                //TODO 그리기
//                Glide.with(imageView).load(faceBitmap).into(imageView)
//            }

//            faceBitmap = resultingImage
//            Glide.with(imageView).load(resultingImage).into(imageView)
            }

        }
        return null
    }

    private fun getBgBitmap(points: ArrayList<FaceContourGraphic.FaceContourData>): Bitmap? {

        originBitmapForBg?.let {bitmap ->

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

//            if (!origin.isRecycled) origin.recycle()
//            if (!resultingImage.isRecycled) resultingImage.recycle()

            bgBitmap = resultingImage
            graphicOverlay.clear()
            return bgBitmap
//            Glide.with(imageView).load(resultingImage).into(imageView)
        }
        return null
    }

//    fun bringResultBitmap(): Bitmap? {
//        return resultBitmap
//    }



}