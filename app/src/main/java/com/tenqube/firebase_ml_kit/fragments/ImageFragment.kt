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

import android.graphics.*
import android.os.Build
import android.os.Bundle
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

    private var resultBitmap1: Bitmap?= null


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
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>) {
                        getBgBitmap(points)
                    }
                })
            }
        }

        view.findViewById<Button>(R.id.put_face_image_btn).setOnClickListener {
            try {
                val file = mediaList[0]
                val faceImage = BitmapFactory.decodeFile(file.absolutePath)
                originBitmapForFace= resizeImage(faceImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        view.findViewById<Button>(R.id.get_face_btn).setOnClickListener {
            originBitmapForFace?.let {
                getVisionPoints(it, object :CallbackGraphic {
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>) {
                        getFaceBitmap(points)
                    }
                })
            }

        }

        view.findViewById<ImageButton>(R.id.change_face_btn).setOnClickListener {
            bgBitmap?.let { bg -> faceBitmap?.let { face -> changeFace(bg, face) } }
        }
    }
    private fun changeFace(bgBitmap: Bitmap, faceBitmap: Bitmap) {

        val resultImage = faceBitmap.copy(bgBitmap.config, true)

//        val canvas = Canvas(resultingImage)
//        canvas.drawARGB(0, 0, 0, 0)
//
//        val paint = Paint()
//        paint.isAntiAlias = true
//
//        val rect = Rect(0, 0, origin.width, origin.height)
//
//        context?.let {
//            val color = ContextCompat.getColor(it, R.color.grey)
//            paint.color = color
//        }
//
//        val path = Path()
//
//        points.forEach {
//            path.lineTo(it.px, it.py)
//        }
//        canvas.drawPath(path, paint)
////            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
//
//        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OUT))
//
//        canvas.drawBitmap(origin/*bitmap2*/, rect, rect, paint)


    }

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
                    override fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>) {
                        callback.getPoints(points)
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
        fun getPoints(points: ArrayList<FaceContourGraphic.FaceContourData>)
    }

    private fun processFaceContourDetectionResultForImage(faces: List<FirebaseVisionFace>, callback: CallbackGraphic ) { // Task completed successfully
        if (faces.isEmpty()) {
            Toast.makeText(context, "not found", Toast.LENGTH_SHORT).show()
            return
        }
        graphicOverlay.clear()
        for (i in faces.indices) {
            val face = faces[i]

            val faceGraphic = FaceContourGraphic(graphicOverlay, face) {
//                originBitmapForBg = firebaseVisionImage.bitmap
//                getBgBitmap(it)
                callback.getPoints(it)
            }

            graphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }


//    private fun setResultImage(points: ArrayList<FaceContourGraphic.FaceContourData>, firebaseVisionImage: FirebaseVisionImage) {
//
//        bitmap?.let {bitmap ->
//
//            val bitmap2 = bitmap//firebaseVisionImage.bitmap//BitmapFactory.decodeResource(getResources(),R.drawable.gallery_12);
//
////            val resultingImage = bitmap2.copy(bitmap2.config, true)  // 얼굴 외 의 사진 얻을경우 (원본 복사)
//            val resultingImage = Bitmap.createBitmap(bitmap2.width,
//                bitmap2.height, Bitmap.Config.ARGB_8888
//            )
//
//            val canvas = Canvas(resultingImage)
//            canvas.drawARGB(0, 0, 0, 0)
//            val paint = Paint()
//            paint.isAntiAlias = true
//
//            val rect = Rect(0, 0, bitmap2.width, bitmap2.height)
//
//            context?.let {
//                val color = ContextCompat.getColor(it, R.color.grey)
//                paint.color = color
//            }
//
//            val path = Path()
//
//            points.forEach {
//                path.lineTo(it.px, it.py)
//                canvas.drawCircle(it.px, it.py, 6.0f, Paint(Color.BLUE))
//            }
////            canvas.clipPath(path)
//            canvas.drawPath(path, paint)
////        if (crop) {
//            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
//
////        } else {
////             paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OUT))
////        }
//
//        canvas.drawBitmap(bitmap2/*bitmap2*/, rect, rect, paint)
//
////        canvas.drawBitmap(bitmap2/*bitmap2*/, 0.toFloat(), 0.toFloat(), paint)
//
//        resultBitmap1 = resultingImage
//        Glide.with(imageView).load(resultingImage).into(imageView)
//
////        imageView.setImageBitmap(resultingImage)
//        }
//
//    }

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

            faceBitmap = resultingImage
            Glide.with(imageView).load(resultingImage).into(imageView)

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


//
//
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