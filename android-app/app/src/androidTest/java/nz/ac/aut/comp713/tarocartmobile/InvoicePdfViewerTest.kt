package nz.ac.aut.comp713.tarocartmobile

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nz.ac.aut.comp713.tarocartmobile.ui.renderPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InvoicePdfViewerTest {
    @Test
    fun rendersEachPageFromAnInvoicePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = File.createTempFile("viewer-test-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            pdf.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }

        try {
            val first = renderPage(pdf, 0, 600)
            val second = renderPage(pdf, 1, 600)
            assertEquals(Color.RED, first.getPixel(first.width / 2, first.height / 2))
            assertEquals(Color.BLUE, second.getPixel(second.width / 2, second.height / 2))
            first.recycle()
            second.recycle()
        } finally {
            pdf.delete()
        }
    }
}
