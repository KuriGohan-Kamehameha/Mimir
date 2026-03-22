package com.mimir.translate.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import com.mimir.translate.BuildConfig
import android.view.WindowManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "Mimir"
    }

    // Use AtomicReference to avoid data races on mediaProjection
    private val mediaProjectionRef = AtomicReference<MediaProjection?>(null)
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    // Callback for when projection is ready
    private var onProjectionReady: (() -> Unit)? = null

    val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val isReady: Boolean
        get() = mediaProjectionRef.get() != null

    fun setProjection(projection: MediaProjection) {
        if (BuildConfig.DEBUG) Log.d(TAG, "MediaProjection received")
        mediaProjectionRef.set(projection)
        onProjectionReady?.invoke()
        onProjectionReady = null
    }

    fun awaitProjectionReady(callback: () -> Unit) {
        if (mediaProjectionRef.get() != null) {
            callback()
        } else {
            onProjectionReady = callback
        }
    }

    suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val projection = mediaProjectionRef.get()
        if (projection == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "captureScreen called but mediaProjection is null")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val metrics = getScreenMetrics()
        check(metrics.widthPixels > 0 && metrics.heightPixels > 0) { "Invalid screen metrics" } // NASA Rule 5
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (BuildConfig.DEBUG) Log.d(TAG, "Capturing screen: ${width}x${height} @ ${density}dpi")

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        val captured = AtomicBoolean(false)

        reader.setOnImageAvailableListener({ imgReader ->
            // Use compareAndSet for atomic check-and-set to prevent double processing
            if (captured.compareAndSet(false, true)) {
                handleImageAvailable(imgReader, reader, width, height, continuation)
            }
        }, handler)

        createVirtualDisplaySafe(projection, reader, width, height, density, continuation)

        continuation.invokeOnCancellation { cleanupCaptureResources(reader) }
    }

    /** NASA Rule 4: extracted image handling from captureScreen. */
    private fun handleImageAvailable(
        imgReader: ImageReader,
        reader: ImageReader,
        width: Int,
        height: Int,
        continuation: CancellableContinuation<Bitmap?>,
    ) {
        val image = imgReader.acquireLatestImage()
        if (image != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Image acquired: ${image.width}x${image.height}")
            val bitmap = imageToBitmap(image, width, height)
            image.close()
            cleanupCaptureResources(reader)
            continuation.resume(bitmap)
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG, "acquireLatestImage returned null")
            cleanupCaptureResources(reader)
            continuation.resume(null)
        }
    }

    /** NASA Rule 4: extracted VirtualDisplay creation. */
    private fun createVirtualDisplaySafe(
        projection: MediaProjection,
        reader: ImageReader,
        width: Int,
        height: Int,
        density: Int,
        continuation: CancellableContinuation<Bitmap?>,
    ) {
        try {
            val display = projection.createVirtualDisplay(
                "MimirCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null,
            )
            virtualDisplay = display
            if (BuildConfig.DEBUG) Log.d(TAG, "VirtualDisplay created")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create VirtualDisplay", e)
            reader.close()
            imageReader = null
            continuation.resume(null)
        }
    }

    /** NASA Rule 4: extracted cleanup into reusable function. */
    private fun cleanupCaptureResources(reader: ImageReader) {
        virtualDisplay?.release()
        reader.close()
        virtualDisplay = null
        imageReader = null
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjectionRef.getAndSet(null)?.stop()
        virtualDisplay = null
        imageReader = null
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        // NASA Rule 5: validate image data
        check(image.planes.isNotEmpty()) { "Image has no planes" }
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        check(pixelStride > 0) { "Invalid pixel stride: $pixelStride" }
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmapWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (bitmapWidth != width) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }
}
