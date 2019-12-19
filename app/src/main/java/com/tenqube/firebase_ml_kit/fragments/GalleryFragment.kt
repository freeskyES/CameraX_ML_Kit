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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import java.io.File
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import com.android.example.cameraxbasic.utils.padWithDisplayCutout
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.drawToBitmap
import com.android.example.cameraxbasic.utils.showImmersive
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.tenqube.firebase_ml_kit.BuildConfig
import com.tenqube.firebase_ml_kit.R
import com.tenqube.firebase_ml_kit.facedetection.FaceContourGraphic
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import java.util.ArrayList


val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    companion object {
        private const val ARG = "ARG"

        fun newInstance(url: String) = GalleryFragment().apply {
            arguments = Bundle().apply {
                putString(ARG, url)
            }
        }

    }
    private lateinit var mediaList: MutableList<File>

//    private lateinit var mediaViewPager: ViewPager
//    private lateinit var mediaViewPagerAdapter: MediaPagerAdapter
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var imageView: ImageView

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        arguments?.let{

            val rootDirectory = File(it.getString(ARG)?: "")

            // Walk through all files in the root directory
            // We reverse the order of the list to present the last photos first
            mediaList = rootDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
            }?.sortedDescending()?.toMutableList() ?: mutableListOf()
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_gallery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate the ViewPager and implement a cache of two media items
//        mediaViewPager = view.findViewById<ViewPager>(R.id.photo_view_pager).apply {
//            offscreenPageLimit = 2
//            mediaViewPagerAdapter = MediaPagerAdapter(childFragmentManager)
//            adapter = mediaViewPagerAdapter
//        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
        }

        // Handle back button press
        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            activity?.let{ it.supportFragmentManager.beginTransaction()
                .replace(R.id.container, CameraFragment2.newInstance())
                .commitNow()}
        }

        // Handle share button press
        view.findViewById<ImageButton>(R.id.share_button).setOnClickListener {
            // Make sure that we have a file to share
            mediaList.getOrNull(mediaList.lastIndex)?.let { mediaFile ->

                // Create a sharing intent
                val intent = Intent().apply {
                    // Infer media type from file extension
                    val mediaType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(mediaFile.extension)
                    // Get URI from our FileProvider implementation
                    val uri = FileProvider.getUriForFile(
                            view.context, BuildConfig.APPLICATION_ID + ".provider", mediaFile)
                    // Set the appropriate intent extra, type, action and flags
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = mediaType
                    action = Intent.ACTION_SEND
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                // Launch the intent letting the user choose which app to share with
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        // Handle delete button press
        view.findViewById<ImageButton>(R.id.delete_button).setOnClickListener {
            AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                    .setTitle(getString(R.string.delete_title))
                    .setMessage(getString(R.string.delete_dialog))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        mediaList.getOrNull(mediaList.lastIndex/*mediaViewPager.currentItem*/)?.let { mediaFile ->

                            // Delete current photo
                            mediaFile.delete()

                            // Send relevant broadcast to notify other apps of deletion
                            MediaScannerConnection.scanFile(
                                    view.context, arrayOf(mediaFile.absolutePath), null, null)

                            // Notify our view pager
                            mediaList.removeAt(/*mediaViewPager.currentItem*/mediaList.lastIndex)
//                            mediaViewPager.adapter?.notifyDataSetChanged()

                            // If all photos have been deleted, return to camera
                            if (mediaList.isEmpty()) {
                                fragmentManager?.popBackStack()
                            }
                        }}

                    .setNegativeButton(android.R.string.no, null)
                    .create().showImmersive()
        }

        graphicOverlay = view.findViewById(R.id.fireFaceOverlay)
        imageView = view.findViewById(R.id.image_view)
//        mediaList.getOrNull(mediaList.lastIndex)?.let{
        context?.let{
            val testImage = it.resources.getDrawable(R.drawable.test_image)

            Glide.with(view).load(testImage).into(imageView)
        }


//        }

        view.findViewById<ImageButton>(R.id.cut_face_button).setOnClickListener {
            cropImage()
        }
    }

    private fun cropImage() {
        mediaList.getOrNull(mediaList.lastIndex)?.let { mediaFile ->
            context?.let {context ->

//                val image = FirebaseVisionImage.fromFilePath(context, mediaFile.toUri())

                val image = FirebaseVisionImage.fromBitmap(imageView.drawToBitmap())
                runFaceContourDetectionForImage(mediaFile, image)

            }
        }

    }

    private fun runFaceContourDetectionForImage(file: File, firebaseVisionImage: FirebaseVisionImage) {
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
                    processFaceContourDetectionResultForImage(file, faces)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun processFaceContourDetectionResultForImage(file: File, faces: List<FirebaseVisionFace>) { // Task completed successfully
        if (faces.isEmpty()) {
            return
        }
        graphicOverlay.clear()
        for (i in faces.indices) {
            val face = faces[i]
//            val contour = face.getContour(FirebaseVisionFaceContour.FACE)

            val faceGraphic = FaceContourGraphic(graphicOverlay, face) { points, faceInfo ->
                setResultImage(file, points)
            }

            graphicOverlay.add(faceGraphic)
//            faceGraphic.updateFace(face)
        }
    }

    private fun setResultImage(file: File, points: ArrayList<FaceContourGraphic.FaceContourData>) {


        val bitmap2 = BitmapFactory.decodeFile(file.path) //BitmapFactory.decodeResource(getResources(),R.drawable.gallery_12);

        val resultingImage = Bitmap.createBitmap(imageView.width,
            imageView.height, bitmap2.config
        )

        val canvas = Canvas(resultingImage)
        val paint = Paint()
        paint.isAntiAlias = true

        val path = Path()

        points.forEach {
            path.lineTo(it.px, it.py)
        }

        canvas.drawPath(path, paint)
//        if (crop) {
//        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))

//        } else {
            paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_OUT))
//        }
        canvas.drawBitmap(bitmap2, 0.toFloat(), 0.toFloat(), paint)
        imageView.setImageBitmap(resultingImage)

    }
}