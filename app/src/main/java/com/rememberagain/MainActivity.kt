package com.rememberagain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rememberagain.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var photo: Bitmap? = null
    private var model: Module? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadPhoto(it) } }

    private val modelPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importModel(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.selectPhoto.setOnClickListener { photoPicker.launch("image/*") }
        binding.selectModel.setOnClickListener { modelPicker.launch(arrayOf("*/*")) }
        binding.detect.setOnClickListener { runDetection() }
    }

    private fun loadPhoto(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodePhoto(uri)
            }
            if (bitmap == null) {
                binding.status.text = "Could not decode the selected photo."
                return@launch
            }
            photo = bitmap
            binding.preview.setImageBitmap(bitmap)
            binding.status.text = "Photo selected. Preserve this original; the mask is a separate preview."
            updateDetectEnabled()
        }
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            try {
                val destination = File(filesDir, "bopbtl.pte")
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the model." }
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val loaded = withContext(Dispatchers.Default) {
                    Module.load(destination.absolutePath, Module.LOAD_MODE_MMAP)
                }
                model = loaded
                binding.status.text = "BOPBTL model loaded. Select Detect scratches to run CPU inference."
                updateDetectEnabled()
            } catch (error: Exception) {
                model = null
                binding.status.text = "Model load failed: ${error.message ?: error.javaClass.simpleName}"
                updateDetectEnabled()
            }
        }
    }

    private fun updateDetectEnabled() {
        binding.detect.isEnabled = photo != null && model != null
    }

    private fun runDetection() {
        val inputPhoto = photo ?: return
        val loadedModel = model ?: return
        binding.detect.isEnabled = false
        binding.status.text = "Running BOPBTL on CPU…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    BopbtlInference.run(inputPhoto, loadedModel)
                }
                binding.preview.setImageBitmap(result.maskPreview)
                binding.status.text = "Inference complete: ${result.elapsedMs} ms, output ${result.width}×${result.height}. Original photo was not modified."
            } catch (error: Exception) {
                binding.status.text = "Inference failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                updateDetectEnabled()
            }
        }
    }

    private fun decodePhoto(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = Integer.highestOneBit((largestDimension / 4096).coerceAtLeast(1))
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    override fun onDestroy() {
        model?.destroy()
        model = null
        photo?.recycle()
        photo = null
        super.onDestroy()
    }
}

private object BopbtlInference {
    data class Result(val maskPreview: Bitmap, val width: Int, val height: Int, val elapsedMs: Long)

    fun run(photo: Bitmap, module: Module): Result {
        val started = System.currentTimeMillis()
        val shortSide = minOf(photo.width, photo.height)
        val scale = 256f / shortSide
        val resizedWidth = align16(ceil(photo.width * scale).toInt())
        val resizedHeight = align16(ceil(photo.height * scale).toInt())
        val resized = Bitmap.createScaledBitmap(photo, resizedWidth, resizedHeight, true)
        val pixels = IntArray(resizedWidth * resizedHeight)
        resized.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)
        val input = FloatArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            val gray = (0.299f * Color.red(color) + 0.587f * Color.green(color) +
                0.114f * Color.blue(color)) / 255f
            input[index] = (gray - 0.5f) / 0.5f
        }
        val tensor = Tensor.fromBlob(input, longArrayOf(1, 1, resizedHeight.toLong(), resizedWidth.toLong()))
        val output = module.forward(EValue.from(tensor)).first().toTensor()
        val values = FloatArray(output.numel().toInt())
        val outputBuffer = Tensor.allocateFloatBuffer(values.size)
        output.copyDataInto(outputBuffer)
        outputBuffer.rewind()
        outputBuffer.get(values)
        val shape = output.shape()
        require(shape.size >= 2) { "Expected a mask tensor, got shape ${shape.contentToString()}" }
        val outputHeight = shape[shape.size - 2].toInt()
        val outputWidth = shape[shape.size - 1].toInt()
        require(values.size >= outputWidth * outputHeight) { "Mask tensor data is smaller than its shape." }
        val mask = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(outputWidth * outputHeight)
        for (index in maskPixels.indices) {
            val probability = if (values[index] in 0f..1f) values[index] else sigmoid(values[index])
            maskPixels[index] = Color.argb((180 * probability).toInt(), 255, 0, 0)
        }
        mask.setPixels(maskPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        val preview = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        Canvas(preview).drawBitmap(photo, 0f, 0f, null)
        val overlay = Bitmap.createScaledBitmap(mask, photo.width, photo.height, true)
        val paint = Paint().apply { alpha = 150 }
        Canvas(preview).drawBitmap(overlay, 0f, 0f, paint)
        return Result(preview, outputWidth, outputHeight, System.currentTimeMillis() - started)
    }

    private fun align16(value: Int): Int = max(16, ((value + 15) / 16) * 16)
    private fun sigmoid(value: Float): Float = (1f / (1f + kotlin.math.exp(-value))).coerceIn(0f, 1f)
}
