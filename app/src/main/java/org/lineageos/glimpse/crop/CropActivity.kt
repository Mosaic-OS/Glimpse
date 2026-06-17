package org.lineageos.glimpse.crop

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import org.lineageos.glimpse.R

class CropActivity : Activity() {

    private lateinit var imageView: ImageView
    private lateinit var cropOverlay: CropOverlayView

    private var sourceBitmap: Bitmap? = null
    private var sourceUri: Uri? = null
    private var outputUri: Uri? = null
    private var outputX: Int = 0
    private var outputY: Int = 0

    private var displayedImageRect = RectF()

    companion object {
        private const val TAG = "CropActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        imageView = findViewById(R.id.imageView)
        cropOverlay = findViewById(R.id.cropOverlay)
        val btnCrop = findViewById<Button>(R.id.btnCrop)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val buttonBar = findViewById<LinearLayout>(R.id.buttonBar)

        ViewCompat.setOnApplyWindowInsetsListener(buttonBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom + (16 * resources.displayMetrics.density).toInt())
            insets
        }

        sourceUri = intent.data ?: run { finish(); return }
        outputUri = intent.getParcelableExtra("output")
        outputX = intent.getIntExtra("outputX", 512)
        outputY = intent.getIntExtra("outputY", 512)
        val aspectX = intent.getIntExtra("aspectX", 1)
        val aspectY = intent.getIntExtra("aspectY", 1)

        loadImage()

        cropOverlay.setAspectRatio(aspectX.toFloat() / aspectY.toFloat())

        btnCrop.setOnClickListener { performCrop() }
        btnCancel.setOnClickListener { setResult(RESULT_CANCELED); finish() }
    }

    private fun loadImage() {
        try {
            val uri = sourceUri ?: return

            val orientation = getExifOrientation(uri)
            contentResolver.openInputStream(uri)?.use { stream ->
                val originalBitmap = BitmapFactory.decodeStream(stream) ?: return
                sourceBitmap = transformBitmap(originalBitmap, orientation)

                imageView.setImageBitmap(sourceBitmap)
                imageView.post {
                    calculateDisplayedImageRect()
                    cropOverlay.setBitmapRect(displayedImageRect)
                }
            } ?: run { finish() }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun getExifOrientation(uri: Uri): Int {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                if (orientation != ExifInterface.ORIENTATION_NORMAL) return orientation
            }
        } catch (_: Exception) {}

        try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.ORIENTATION), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val degreeIndex = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                    if (degreeIndex != -1) {
                        return when (cursor.getInt(degreeIndex)) {
                            90 -> ExifInterface.ORIENTATION_ROTATE_90
                            180 -> ExifInterface.ORIENTATION_ROTATE_180
                            270 -> ExifInterface.ORIENTATION_ROTATE_270
                            else -> ExifInterface.ORIENTATION_NORMAL
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return ExifInterface.ORIENTATION_NORMAL
    }

    private fun transformBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }

        return try {
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed != bitmap) bitmap.recycle()
            transformed
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    private fun calculateDisplayedImageRect() {
        val drawable = imageView.drawable ?: return
        val values = FloatArray(9)
        imageView.imageMatrix.getValues(values)

        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        displayedImageRect.set(transX, transY, transX + (drawable.intrinsicWidth * scaleX), transY + (drawable.intrinsicHeight * scaleY))
    }

    private fun performCrop() {
        val bitmap = sourceBitmap ?: return
        try {
            val cropRect = cropOverlay.getCropRect()
            val scaleX = bitmap.width.toFloat() / displayedImageRect.width()
            val scaleY = bitmap.height.toFloat() / displayedImageRect.height()

            val cropX = ((cropRect.left - displayedImageRect.left) * scaleX).toInt().coerceIn(0, bitmap.width)
            val cropY = ((cropRect.top - displayedImageRect.top) * scaleY).toInt().coerceIn(0, bitmap.height)
            val cropW = (cropRect.width() * scaleX).toInt().coerceAtMost(bitmap.width - cropX).coerceAtLeast(1)
            val cropH = (cropRect.height() * scaleY).toInt().coerceAtMost(bitmap.height - cropY).coerceAtLeast(1)

            val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)

            val finalBitmap = if (outputX > 0 && outputY > 0) croppedBitmap.scale(outputX, outputY) else croppedBitmap

            val destinationUri = outputUri ?: sourceUri ?: return
            contentResolver.openOutputStream(destinationUri)?.use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            setResult(RESULT_OK, Intent().apply { data = destinationUri })

            if (finalBitmap != croppedBitmap) croppedBitmap.recycle()
            if (finalBitmap != bitmap) finalBitmap.recycle()

            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping", e)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceBitmap?.recycle()
        sourceBitmap = null
    }
}