package com.miaclean.app.data.hash
import android.content.Context; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.net.Uri; import dagger.hilt.android.qualifiers.ApplicationContext; import ru.avicorp.phashcalc.pHashCalc; import java.io.File; import javax.inject.Inject; import javax.inject.Singleton
@Singleton
class InternalPerceptualHasher @Inject constructor(@ApplicationContext private val context: Context) {
    private val phash by lazy { pHashCalc() }
    fun hash(uri: Uri): String? {
        val cacheDir = File(context.cacheDir, "phash").apply { mkdirs() }; val tmp = File.createTempFile("phash_", ".jpg", cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                tmp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                bitmap.recycle()
            } ?: return null
            if (!phash.loadSourceFile(tmp.absolutePath, tmp.absolutePath)) return null
            if (!phash.checkCondition()) return null
            phash.getHashOne()
        } catch (_: Throwable) { null } finally { tmp.delete() }
    }
}
