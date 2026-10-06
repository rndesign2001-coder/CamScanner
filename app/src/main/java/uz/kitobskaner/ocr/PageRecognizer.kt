package uz.kitobskaner.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.googlecode.tesseract.android.TessBaseAPI
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.image.GrayImage
import uz.kitobskaner.image.ImageProcessing
import uz.kitobskaner.image.ImageUtils
import java.io.Closeable
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bitta Tesseract nusxasi. Thread-safe emas — har bir oqim o'z nusxasidan foydalanadi. */
class OcrEngine(context: Context, val language: String) : Closeable {
    private val tess = TessBaseAPI()

    init {
        TessData.ensure(context)
        if (!tess.init(TessData.dataPath(context), language, TessBaseAPI.OEM_LSTM_ONLY)) {
            tess.recycle()
            throw IllegalStateException("Tesseract init failed: $language")
        }
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        tess.setVariable("user_defined_dpi", "300")
        tess.setVariable("preserve_interword_spaces", "0")
        // teskari (oq-ustida-qora emas) matnni qidirmaslik — 20–30% tezroq
        tess.setVariable("tessedit_do_invert", "0")
    }

    /** @return hOCR va o'rtacha ishonch (0..100) */
    fun hocr(gray: GrayImage): Pair<String, Int> {
        tess.setImage(gray.data, gray.width, gray.height, 1, gray.width)
        val h = tess.getHOCRText(0) ?: ""
        val conf = try { tess.meanConfidence() } catch (e: Exception) { -1 }
        tess.clear()
        return h to conf
    }

    fun text(gray: GrayImage): String {
        tess.setImage(gray.data, gray.width, gray.height, 1, gray.width)
        val t = tess.getUTF8Text() ?: ""
        tess.clear()
        return t
    }

    fun stop() = tess.stop()

    override fun close() = tess.recycle()
}

/** Sahifani to'liq qayta ishlash: tasvirni tayyorlash → OCR → qalin matn → tuzilma. */
class PageRecognizer(private val context: Context) {

    class Prepared(val norm: GrayImage, val scale: Float)

    /**
     * OCR uchun tasvir: yorug'lik tekislangan kulrang. Satrlar orasidagi qadam o'lchanib,
     * matn Tesseract uchun eng qulay o'lchamga (x-balandlik ≈ 25–30 px) keltiriladi —
     * kichik shriftli yoki uzoqdan olingan suratlarda aniqlik sezilarli oshadi.
     */
    fun prepare(pageFile: File, maxSide: Int = OCR_MAX_SIDE, adaptive: Boolean = true): Prepared? {
        val (ow, oh) = ImageUtils.imageSize(pageFile)
        var bmp = ImageUtils.decodeFile(pageFile, maxSide) ?: return null
        var norm = ImageProcessing.normalize(ImageProcessing.toGray(bmp))
        if (adaptive) {
            val pitch = ImageProcessing.linePitch(norm)
            if (pitch != null) {
                // faqat aniq kichik (qadam < 40 px) yoki juda katta (> 110 px) matnda o'lcham o'zgartiriladi
                val k = when {
                    pitch < 40f -> (TARGET_PITCH / pitch).coerceAtMost(2.6f)
                    pitch > 110f -> (TARGET_PITCH / pitch).coerceAtLeast(0.5f)
                    else -> 1f
                }
                val longSide = max(bmp.width, bmp.height)
                val target = (longSide * k).toInt().coerceIn(1200, MAX_ADAPTIVE_SIDE)
                if (abs(target.toFloat() / longSide - 1f) > 0.15f) {
                    val originalLong = max(ow, oh)
                    val scaled = if (target < longSide || originalLong <= longSide) {
                        val f = target.toFloat() / longSide
                        android.graphics.Bitmap.createScaledBitmap(
                            bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true
                        )
                    } else {
                        // asl fayl kattaroq — undan qayta o'qiymiz (sifatliroq)
                        ImageUtils.decodeFile(pageFile, target)?.let { d ->
                            if (max(d.width, d.height) < target) {
                                val f = target.toFloat() / max(d.width, d.height)
                                android.graphics.Bitmap.createScaledBitmap(d, (d.width * f).toInt(), (d.height * f).toInt(), true)
                                    .also { if (it !== d) d.recycle() }
                            } else d
                        }
                    }
                    if (scaled != null) {
                        if (scaled !== bmp) bmp.recycle()
                        bmp = scaled
                        norm = ImageProcessing.normalize(ImageProcessing.toGray(bmp))
                    }
                }
            }
        }
        val scale = if (ow > 0) bmp.width.toFloat() / ow else 1f
        bmp.recycle()
        return Prepared(norm, scale)
    }

    fun recognize(
        engine: OcrEngine,
        pageFile: File,
        pageId: String,
        imagesDir: File,
        settings: AppSettings,
        maxSide: Int = OCR_MAX_SIDE,
    ): OcrPage {
        val prepared = prepare(pageFile, maxSide) ?: return OcrPage(0, 0, engine.language, emptyList())
        val norm = prepared.norm
        val (hocr, confidence) = engine.hocr(norm)

        val page = try {
            HocrParser.parse(hocr, norm.width, norm.height)
        } catch (e: Exception) {
            null
        }
        if (page == null) {
            // zaxira: oddiy matn
            val text = engine.text(norm)
            val blocks = text.split(Regex("\\n\\s*\\n")).map { it.replace('\n', ' ').trim() }
                .filter { it.isNotEmpty() }
                .map { TextBlock(BlockType.PARAGRAPH, listOf(uz.kitobskaner.data.TextSpan(it))) }
            return OcrPage(norm.width, norm.height, engine.language, blocks, emptyList(), confidence)
        }

        val ink = ImageProcessing.binarize(norm)
        BoldDetector.detect(page, ink, norm.width, norm.height, settings.boldSensitivity)

        val layout = LayoutAnalyzer.analyze(
            page, engine.language,
            LayoutOptions(settings.removeHeaders, settings.keepImages, settings.fixUzbekApostrophe)
        )

        val blocks = ArrayList<TextBlock>()
        var imageIndex = 0
        for (item in layout.items) {
            when (item) {
                is LayoutItem.Text -> if (item.block.spans.isNotEmpty()) blocks += item.block
                is LayoutItem.Image -> {
                    val name = "${pageId}_${imageIndex++}.jpg"
                    val aspect = cropImage(pageFile, item.box, prepared.scale, File(imagesDir, name))
                    if (aspect > 0f) blocks += TextBlock(BlockType.IMAGE, image = name, imageAspect = aspect)
                }
            }
        }
        return OcrPage(norm.width, norm.height, engine.language, blocks, layout.words, confidence)
    }

    /** Rasm hududini asl fayldan kesib saqlaydi. @return eni/bo'yi nisbati yoki 0 */
    private fun cropImage(pageFile: File, box: Box, scale: Float, out: File): Float {
        return try {
            val decoder = if (Build.VERSION.SDK_INT >= 31) {
                BitmapRegionDecoder.newInstance(pageFile.absolutePath)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(pageFile.absolutePath, false)
            } ?: return 0f
            val pad = 4
            val rect = Rect(
                max(0, ((box.l - pad) / scale).roundToInt()),
                max(0, ((box.t - pad) / scale).roundToInt()),
                min(decoder.width, ((box.r + pad) / scale).roundToInt()),
                min(decoder.height, ((box.b + pad) / scale).roundToInt()),
            )
            if (rect.width() < 8 || rect.height() < 8) { decoder.recycle(); return 0f }
            val opts = BitmapFactory.Options().apply {
                var s = 1
                while (max(rect.width(), rect.height()) / (s * 2) >= 1600) s *= 2
                inSampleSize = s
            }
            val bmp = decoder.decodeRegion(rect, opts)
            decoder.recycle()
            if (bmp == null) return 0f
            out.parentFile?.mkdirs()
            ImageUtils.saveJpeg(bmp, out, 88)
            val aspect = bmp.width.toFloat() / bmp.height
            bmp.recycle()
            aspect
        } catch (e: Exception) {
            0f
        }
    }

    companion object {
        const val OCR_MAX_SIDE = 3000
        /** Satrlar orasidagi maqbul masofa (px). */
        private const val TARGET_PITCH = 52f
        private const val MAX_ADAPTIVE_SIDE = 4600
    }
}
