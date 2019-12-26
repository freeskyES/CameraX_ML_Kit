package com.tenqube.firebase_ml_kit.fragments

import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.android.example.cameraxbasic.utils.padWithDisplayCutout
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.tenqube.firebase_ml_kit.R
import com.tenqube.firebase_ml_kit.facedetection.FaceContourGraphic
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import com.tenqube.firebase_ml_kit.utils.GlideApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*


val EXTENSION_WHITELIST_1 = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class ImageFragment internal constructor() : Fragment() {
    private lateinit var mediaList: MutableList<File>

    lateinit var file: File
    private var bitmap: Bitmap? = null

    private var originBitmapForFace : Bitmap? = null
    private var faceBitmap : Bitmap? = null

    private var originBitmapForBg : Bitmap? = null
    private var bgBitmap : Bitmap? = null

    private var bgFaceInfo: FaceContourGraphic.FaceDetectInfo? = null
    private var myFaceInfo: FaceContourGraphic.FaceDetectInfo? = null

    private var finalImageBitmap: Bitmap?= null


    companion object {
        private const val ARG = "ARG"

        fun newInstance(url: String) = ImageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG, url)
            }
        }

    }
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        arguments?.let{

            val rootDirectory = File(it.getString(ARG)?: "")
            mediaList = rootDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
            }?.sortedDescending()?.toMutableList() ?: mutableListOf()
            file = mediaList[0]
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_image, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
        }

        // Handle back button press
        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            goCameraFragment()
        }

        graphicOverlay = view.findViewById(R.id.fireFaceOverlay)
        imageView = view.findViewById(R.id.image_view)
        context?.let{
//            Glide.with(view).load(testImage).into(imageView)
        }

        view.findViewById<Button>(R.id.put_bg_image_btn).setOnClickListener {
            val testImage = it.resources.getDrawable(R.drawable.test_model_2)
            originBitmapForBg = resizeImage(testImage.toBitmap())
        }

        view.findViewById<Button>(R.id.get_bg_btn).setOnClickListener {
            originBitmapForBg?.let{
                getVisionPoints(it, object :CallbackGraphic {
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                           faceInfo: FaceContourGraphic.FaceDetectInfo) {
                        getBgBitmap(points)
                        bgFaceInfo = faceInfo
                    }
                })
            }
        }

        view.findViewById<Button>(R.id.put_face_image_btn).setOnClickListener {
            try {
                graphicOverlay.clear()

                val file = mediaList[0]
                GlobalScope.launch(Dispatchers.IO) {
                    val faceImage = GlideApp.with(it).asBitmap().load(file).submit().get()
                    //BitmapFactory.decodeFile(file.absolutePath) // TODO orientation 회전고려 안해줌 Glide 쓰기

                    withContext(Dispatchers.Main) {
                        originBitmapForFace= resizeImage(faceImage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        view.findViewById<Button>(R.id.get_face_btn).setOnClickListener {
            originBitmapForFace?.let {
                getVisionPoints(it, object :CallbackGraphic {
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>,
                                           faceInfo: FaceContourGraphic.FaceDetectInfo) {
                        myFaceInfo = faceInfo
                        getFaceBitmap(points)
                    }
                })
            }

        }

        view.findViewById<ImageButton>(R.id.change_face_btn).setOnClickListener {
            bgBitmap?.let { bg -> faceBitmap?.let { face -> bgFaceInfo?.let { bgFace -> myFaceInfo?.let { myFace -> changeFace(bg, face, bgFace, myFace) } } } }
        }
    }

    private fun changeFace(bgBitmap: Bitmap, faceBitmap: Bitmap, bgFaceInfo: FaceContourGraphic.FaceDetectInfo, myFaceInfo: FaceContourGraphic.FaceDetectInfo) {
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
        Glide.with(imageView).load(finalImageBitmap).into(imageView)

    }

//    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
//        val width = bm.width
//        val height = bm.height
//        val scaleWidth = newWidth.toFloat() / width
//        val scaleHeight = newHeight.toFloat() / height
//        // CREATE A MATRIX FOR THE MANIPULATION
//        val matrix = Matrix()
//        // RESIZE THE BIT MAP
//        matrix.postScale(scaleWidth, scaleHeight)
//
//        // "RECREATE" THE NEW BITMAP
//        val resizedBitmap = Bitmap.createBitmap(
//            bm, 0, 0, width, height, matrix, false)
//        bm.recycle()
//        return resizedBitmap
//    }

    private fun goCameraFragment() {
        activity?.let {
            it.supportFragmentManager.beginTransaction()
                .add(R.id.container, CameraFragment2.newInstance())
                .commitNow()
        }
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

        val targetedSize: Pair<Int, Int> = Pair(imageView.width, imageView.height)
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
        Glide.with(imageView).load(resizedBitmap).into(imageView)
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
                    Toast.makeText(context, "fail", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "not found", Toast.LENGTH_SHORT).show()
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

    private fun getFaceBitmap(points: ArrayList<FaceContourGraphic.FaceContourData>) {

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


            // 얼굴크기로 bitmap resize 하기
            myFaceInfo?.run {
                val result = Bitmap.createBitmap(resultingImage, this.left.toInt(), this.top.toInt(), this.rectWidth.toInt(), this.rectHeight.toInt())
                faceBitmap = result
                graphicOverlay.clear()
                Glide.with(imageView).load(faceBitmap).into(imageView)
            }

//            faceBitmap = resultingImage
//            Glide.with(imageView).load(resultingImage).into(imageView)

        }
    }

    private fun getBgBitmap(points: ArrayList<FaceContourGraphic.FaceContourData>) {

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

            bgBitmap = resultingImage
            Glide.with(imageView).load(resultingImage).into(imageView)

        }
    }


//    private fun setResultImage(file: File, points: ArrayList<FaceContourGraphic.FaceContourData>) {
//
//
//        val bitmap2 = BitmapFactory.decodeFile(file.path) //BitmapFactory.decodeResource(getResources(),R.drawable.gallery_12);
//
//        val resultingImage = Bitmap.createBitmap(bitmap2.width,
//            bitmap2.height, bitmap2.config
//        )
//
//        val canvas = Canvas(resultingImage)
//        val paint = Paint()
//        paint.isAntiAlias = true
//
//        val path = Path()
//
//        points.forEach {
//            path.lineTo(it.px, it.py)
//        }
//
//        canvas.drawPath(path, paint)
////        if (crop) {
//        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
//
////        } else {
////            paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OUT))
////        }
//        canvas.drawBitmap(bitmap2, 0.toFloat(), 0.toFloat(), paint)
//        imageView.setImageBitmap(resultingImage)
//
//    }
}