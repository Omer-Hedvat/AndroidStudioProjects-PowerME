package com.powerme.app.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TextRecognitionService @Inject constructor() {

    /**
     * Runs ML Kit on-device OCR on the image at [imageUri].
     * Returns the concatenated text, or an empty string on failure.
     */
    suspend fun recognizeText(imageUri: Uri, context: Context): String {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        cont.resume(result.textBlocks.joinToString("\n") { it.text })
                    }
                    .addOnFailureListener { cont.resume("") }
            }
        } catch (_: Exception) {
            ""
        }
    }
}
