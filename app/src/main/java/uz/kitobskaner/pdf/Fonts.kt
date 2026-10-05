package uz.kitobskaner.pdf

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import uz.kitobskaner.data.FontChoice

class FontSet(val regular: Typeface, val bold: Typeface, val italic: Typeface, val boldItalic: Typeface) {
    fun pick(bold: Boolean, italic: Boolean) = when {
        bold && italic -> boldItalic
        bold -> this.bold
        italic -> this.italic
        else -> regular
    }
}

object Fonts {
    private val cache = HashMap<FontChoice, FontSet>()

    @Synchronized
    fun get(context: Context, choice: FontChoice): FontSet = cache.getOrPut(choice) {
        val base = if (choice == FontChoice.SERIF) "NotoSerif" else "NotoSans"
        fun load(style: String, fallback: Int): Typeface = try {
            Typeface.createFromAsset(context.assets, "fonts/$base-$style.ttf")
        } catch (e: Exception) {
            Typeface.create(if (choice == FontChoice.SERIF) Typeface.SERIF else Typeface.SANS_SERIF, fallback)
        }
        FontSet(
            load("Regular", Typeface.NORMAL),
            load("Bold", Typeface.BOLD),
            load("Italic", Typeface.ITALIC),
            load("BoldItalic", Typeface.BOLD_ITALIC),
        )
    }
}

/** Haqiqiy (sun'iy emas) qalin/kursiv shriftni qo'llaydigan span. */
class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = typeface
    }

    override fun updateMeasureState(tp: TextPaint) {
        tp.typeface = typeface
    }
}
