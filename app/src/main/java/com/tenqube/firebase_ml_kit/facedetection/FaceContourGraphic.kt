package com.tenqube.firebase_ml_kit.facedetection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.Size
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark
import com.tenqube.firebase_ml_kit.facedetection.common.GraphicOverlay
import kotlin.math.abs

typealias FaceContourListener = (points: ArrayList<FaceContourGraphic.FaceContourData>) -> Unit

/** Graphic instance for rendering face contours graphic overlay view.  */
class FaceContourGraphic(overlay: GraphicOverlay, private var firebaseVisionFace: FirebaseVisionFace?,
                         private var listener: FaceContourListener? = null) :
    GraphicOverlay.Graphic(overlay) {

//    private val listeners = ArrayList<FaceContourListener>().apply { listener?.let { add(it) } }
    private val list = ArrayList<FaceContourData>().apply { emptyList<FaceContourData>() }

    private val facePositionPaint: Paint
    private val idPaint: Paint
    private val boxPaint: Paint

    init {
        val selectedColor = Color.WHITE

        facePositionPaint = Paint()
        facePositionPaint.color = selectedColor

        idPaint = Paint()
        idPaint.color = selectedColor
        idPaint.textSize = ID_TEXT_SIZE

        boxPaint = Paint()
        boxPaint.color = selectedColor
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = BOX_STROKE_WIDTH
    }

    fun updateFace(face: FirebaseVisionFace) {
        firebaseVisionFace = face
        postInvalidate()
    }

    /** Draws the face annotations for position on the supplied canvas.  */
    override fun draw(canvas: Canvas) {
        val face = firebaseVisionFace ?: return

        // Draws a circle at the position of the detected face, with the face's track id below.
        val x = translateXForCenter(face.boundingBox.centerX().toFloat())
        val y = translateYForCenter(face.boundingBox.centerY().toFloat())
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint)
        canvas.drawText("id: ${face.trackingId}", x + ID_X_OFFSET, y + ID_Y_OFFSET, idPaint)

        // Draws a bounding box around the face.
        val xOffset = scaleX(face.boundingBox.width() / 2.0f)
        val yOffset = scaleY(face.boundingBox.height() / 2.0f)
        val left = x - xOffset
        val top = y - yOffset
        val right = x + xOffset
        val bottom = y + yOffset
        canvas.drawRect(left, top, right, bottom, boxPaint)

//        val contourForCenter = face.getContour(FirebaseVisionFaceContour.)
//        for (point in contourForCenter.points) {
//            canvas.drawCircle(translateX(point.x), translateY(point.y), FACE_POSITION_RADIUS, facePositionPaint)
//        }
        val faceCenterData = FaceContourData(translateX(face.boundingBox.centerX().toFloat()), translateY(face.boundingBox.centerY().toFloat()))
        val centerSizeDelta = FaceContourData(x - faceCenterData.px, y - faceCenterData.py)
        canvas.drawCircle(faceCenterData.px, faceCenterData.py, FACE_POSITION_RADIUS, facePositionPaint)


        val contour = face.getContour(FirebaseVisionFaceContour.FACE)
        var i = 0
        for (point in contour.points) {
            val px = translateX(point.x)
            val py = translateY(point.y)
            val points= transPoints(FaceContourData(px, py), centerSizeDelta)
            Log.i("draw"," ${px} $py $x $y $point")
            canvas.drawCircle(points.px/*px*/, points.py /*py*/, FACE_POSITION_RADIUS, facePositionPaint)
//            canvas.drawCircle(px, py, FACE_POSITION_RADIUS, facePositionPaint)
//            canvas.drawText("id: ${i}", px -30, py+ 30, idPaint)

            list.add(points/*FaceContourData(px, py)*/)
            i++
        }
        listener?.let {
            list.add(list[0])
            it(list)
            return
        }

        if (face.smilingProbability >= 0) {
            canvas.drawText(
                    "happiness: ${String.format("%.2f", face.smilingProbability)}",
                    x + ID_X_OFFSET * 3,
                    y - ID_Y_OFFSET,
                    idPaint)
        }

        if (face.rightEyeOpenProbability >= 0) {
            canvas.drawText(
                    "right eye: ${String.format("%.2f", face.rightEyeOpenProbability)}",
                    x - ID_X_OFFSET,
                    y,
                    idPaint)
        }
        if (face.leftEyeOpenProbability >= 0) {
            canvas.drawText(
                    "left eye: ${String.format("%.2f", face.leftEyeOpenProbability)}",
                    x + ID_X_OFFSET * 6,
                    y,
                    idPaint)
        }
        val leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE)
        leftEye?.position?.let {
            canvas.drawCircle(
                    translateX(it.x),
                    translateY(it.y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint)
        }
        val rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE)
        rightEye?.position?.let {
            canvas.drawCircle(
                    translateX(it.x),
                    translateY(it.y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint)
        }
        val leftCheek = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_CHEEK)
        leftCheek?.position?.let {
            canvas.drawCircle(
                    translateX(it.x),
                    translateY(it.y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint)
        }

        val rightCheek = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK)
        rightCheek?.position?.let {
            canvas.drawCircle(
                    translateX(it.x),
                    translateY(it.y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint)
        }
    }
    private fun transPoints(beforeSize :FaceContourData, delta: FaceContourData): FaceContourData {

        val resultPx = beforeSize.px + delta.px
        val resultPy =  beforeSize.py + delta.py

        return FaceContourData(resultPx, resultPy)
    }

    companion object {

        private const val FACE_POSITION_RADIUS = 4.0f
        private const val ID_TEXT_SIZE = 30.0f
        private const val ID_Y_OFFSET = 80.0f
        private const val ID_X_OFFSET = -70.0f
        private const val BOX_STROKE_WIDTH = 5.0f
    }

    data class FaceContourData(val px: Float,
                               val py: Float)
}
