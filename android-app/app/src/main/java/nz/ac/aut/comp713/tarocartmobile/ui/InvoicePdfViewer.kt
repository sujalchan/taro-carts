package nz.ac.aut.comp713.tarocartmobile.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.ac.aut.comp713.tarocartmobile.data.AllocationApi
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

private data class InvoiceDocument(val file: File, val pageCount: Int)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun InvoicePdfViewer(allocationId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    var document by remember(allocationId) { mutableStateOf<InvoiceDocument?>(null) }
    var pageIndex by remember(allocationId) { mutableIntStateOf(0) }
    var pageBitmap by remember(allocationId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(allocationId) { mutableStateOf(true) }
    var error by remember(allocationId) { mutableStateOf<String?>(null) }
    var zoom by remember(allocationId) { mutableStateOf(1f) }
    var retryKey by remember(allocationId) { mutableIntStateOf(0) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    BackHandler(onBack = onBack)

    LaunchedEffect(allocationId, retryKey) {
        document = null
        pageBitmap = null
        pageIndex = 0
        loading = true
        error = null
        var downloaded: File? = null
        try {
            val invoiceFile = AllocationApi.downloadInvoice(context.cacheDir, allocationId)
            downloaded = invoiceFile
            val count = withContext(Dispatchers.IO) {
                withPdfRenderer(invoiceFile) { it.pageCount }
            }
            if (count < 1) throw IOException("The invoice PDF has no pages.")
            document = InvoiceDocument(invoiceFile, count)
        } catch (exception: CancellationException) {
            downloaded?.delete()
            throw exception
        } catch (exception: Exception) {
            downloaded?.delete()
            error = exception.message ?: "Could not open the invoice PDF."
        } finally {
            loading = false
        }
    }

    DisposableEffect(document) {
        val file = document?.file
        onDispose { file?.delete() }
    }

    LaunchedEffect(document, pageIndex, screenWidthPx) {
        pageBitmap = null
        val current = document ?: return@LaunchedEffect
        try {
            pageBitmap = withContext(Dispatchers.IO) {
                renderPage(current.file, pageIndex, screenWidthPx * 2)
            }
            error = null
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception.message ?: "Could not display this PDF page."
        }
    }

    LaunchedEffect(pageIndex) {
        verticalScroll.scrollTo(0)
        horizontalScroll.scrollTo(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice #$allocationId") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { retryKey++ }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Try again")
                    }
                }
                document != null -> {
                    val current = requireNotNull(document)
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Previous") }
                        Text("${pageIndex + 1} / ${current.pageCount}")
                        OutlinedButton(onClick = { pageIndex++ }, enabled = pageIndex < current.pageCount - 1) {
                            Text("Next")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f) {
                            Text("Zoom out")
                        }
                        Text("${(zoom * 100).roundToInt()}%", modifier = Modifier.padding(horizontal = 12.dp))
                        TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(2f) }, enabled = zoom < 2f) {
                            Text("Zoom in")
                        }
                    }
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val displayWidth = maxWidth * zoom
                        Box(
                            Modifier.fillMaxSize().verticalScroll(verticalScroll).horizontalScroll(horizontalScroll),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val bitmap = pageBitmap
                            if (bitmap == null) {
                                CircularProgressIndicator(Modifier.padding(32.dp))
                            } else {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Invoice page ${pageIndex + 1} of ${current.pageCount}",
                                    modifier = Modifier.width(displayWidth),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private inline fun <T> withPdfRenderer(file: File, block: (PdfRenderer) -> T): T {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (exception: Exception) {
        descriptor.close()
        throw exception
    }
    return renderer.use(block)
}

internal fun renderPage(file: File, index: Int, targetWidth: Int): Bitmap =
    withPdfRenderer(file) { renderer ->
        renderer.openPage(index).use { page ->
            val scale = minOf(
                targetWidth.toDouble() / page.width,
                2200.0 / page.width,
                3200.0 / page.height
            )
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (exception: Exception) {
                bitmap.recycle()
                throw exception
            }
        }
    }
