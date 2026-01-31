package com.image.resizer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.image.resizer.R
import com.image.resizer.logic.ConversionEngine
import com.image.resizer.logic.ConversionOutput
import com.image.resizer.logic.FileUtil
import com.image.resizer.logic.UnsupportedFormatException
import com.image.resizer.ui.components.*
import com.image.resizer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CompressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // States with Persistence
    var targetSize by rememberSaveable { mutableStateOf("") }
    var sizeUnit by rememberSaveable { mutableStateOf("kb") }
    var compressionMode by rememberSaveable { mutableStateOf("standard") }
    var targetFormat by rememberSaveable { mutableStateOf("jpg") }
    var enableCustomFormat by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }
    var enableDimensions by rememberSaveable { mutableStateOf(false) }
    var dimUnit by rememberSaveable { mutableStateOf("px") }
    var widthVal by rememberSaveable { mutableStateOf("") }
    var heightVal by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf("Ready to start") }
    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var pdfMode by rememberSaveable { mutableStateOf("merge") } // merge or separate
    var batchTargetMode by rememberSaveable { mutableStateOf("per_file") }
    var isFileListExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog State for warnings and file selection
    var unsupportedError by remember { mutableStateOf<String?>(null) }
    var showFileSelectionOptions by remember { mutableStateOf(false) }

    val processingProgressState = rememberSaveable { mutableFloatStateOf(0f) }

    var originalName by rememberSaveable { mutableStateOf("") }
    var originalSize by rememberSaveable { mutableStateOf("") }

    val uriListSaver = listSaver<MutableState<List<Uri>>, String>(
        save = { state -> state.value.map { it.toString() } },
        restore = { list -> mutableStateOf(list.map { it.toUri() }) }
    )
    val selectedUrisState = rememberSaveable(saver = uriListSaver) { mutableStateOf(emptyList()) }
    var selectedUris by selectedUrisState

    // Persistent Results Saver
    val resultsSaver = listSaver<MutableState<List<ConversionOutput>>, String>(
        save = { state ->
            state.value.flatMap { listOf(it.fileName, it.location, it.sizeBytes.toString(), it.uri?.toString() ?: "", it.isBestEffort.toString()) }
        },
        restore = { list ->
            val results = mutableListOf<ConversionOutput>()
            for (i in list.indices step 5) {
                results.add(ConversionOutput(list[i], list[i+1], list[i+2].toLong(), if (list[i+3].isNotEmpty()) list[i+3].toUri() else null, list[i+4].toBoolean()))
            }
            mutableStateOf(results)
        }
    )
    val compressedResultsState = rememberSaveable(saver = resultsSaver) { mutableStateOf(emptyList()) }
    var compressedResults by compressedResultsState

    var fullScreenUri by remember { mutableStateOf<Uri?>(null) }

    // Visibility logic for PDF Options: only show if multiple files are selected AND PDF is involved
    val showPdfOptions = remember(selectedUris, targetFormat, enableCustomFormat) {
        selectedUris.size > 1 && (selectedUris.any { uri -> FileUtil.getFileName(context, uri).lowercase().endsWith(".pdf") } ||
                (enableCustomFormat && targetFormat == "pdf"))
    }

    // Fast and smooth progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = processingProgressState.floatValue,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "progress"
    )

    fun handleUrisSelected(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            selectedUris = uris
            originalName = if (uris.size == 1) FileUtil.getFileName(context, uris[0]) else "${uris.size} Files Selected"
            originalSize = if (uris.size == 1) FileUtil.getFileSize(context, uris[0]) else "Batch Mode"
            statusMessage = "${uris.size} Files Ready"
            compressedResults = emptyList()
            processingProgressState.floatValue = 0f
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        showFileSelectionOptions = false
        handleUrisSelected(uris)
    }

    val fileManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        showFileSelectionOptions = false
        handleUrisSelected(uris)
    }

    fun resetAll() {
        selectedUris = emptyList()
        compressedResults = emptyList()
        targetSize = ""
        customName = ""
        widthVal = ""
        heightVal = ""
        enableCustomFormat = false
        enableDimensions = false
        statusMessage = "Ready to start"
        processingProgressState.floatValue = 0f
        originalName = ""
        originalSize = ""
        unsupportedError = null
        batchTargetMode = "per_file"
        isFileListExpanded = false
    }

    fun performCompression(format: String?) {
        scope.launch {
            try {
                isProcessing = true
                processingProgressState.floatValue = 0f
                statusMessage = "Step 1: Preparing..."
                val targetVal = targetSize.toLongOrNull() ?: 0L
                val targetBytes = if (sizeUnit == "kb") targetVal * 1024 else targetVal * 1024 * 1024

                val outputs = ConversionEngine.compressAndSave(
                    context = context,
                    uris = selectedUris,
                    targetSizeBytes = if (targetVal > 0) targetBytes else 0L,
                    targetFormat = format,
                    isHighMode = compressionMode == "best",
                    newWidth = if (enableDimensions) widthVal.toIntOrNull() else null,
                    newHeight = if (enableDimensions) heightVal.toIntOrNull() else null,
                    customName = customName.ifBlank { null },
                    isMergeMode = pdfMode == "merge",
                    isTotalSize = batchTargetMode == "total"
                ) { progress, message ->
                    statusMessage = message
                    processingProgressState.floatValue = progress
                }

                processingProgressState.floatValue = 1f
                delay(600)
                compressedResults = outputs
                isProcessing = false
                statusMessage = if (outputs.isNotEmpty()) "✅ Compression Complete" else "❌ Compression Failed"
            } catch (e: UnsupportedFormatException) {
                isProcessing = false
                unsupportedError = e.reason
            } catch (e: Exception) {
                isProcessing = false
                statusMessage = "❌ Error: ${e.localizedMessage}"
            }
        }
    }

    if (unsupportedError != null) {
        AlertDialog(
            onDismissRequest = { unsupportedError = null },
            containerColor = SurfaceColor,
            title = { Text("Compression Error", color = TextWhite) },
            text = {
                Text(
                    "$unsupportedError\n\nWould you like to compress to JPEG instead, or cancel?",
                    color = TextDim
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        unsupportedError = null
                        performCompression("jpg")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Compress (JPEG)")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetAll() }) {
                    Text("Cancel", color = Color.Red)
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    if (showFileSelectionOptions) {
        AlertDialog(
            onDismissRequest = { showFileSelectionOptions = false },
            containerColor = SurfaceColor,
            title = { Text("Select Files From", color = TextWhite) },
            text = { Text("Choose how you want to select your images/PDFs.", color = TextDim) },
            confirmButton = {
                Button(
                    onClick = {
                        galleryLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery App Icon")
                        Text("Gallery App")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        fileManagerLauncher.launch(arrayOf("image/*", "application/pdf"))
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = "File Manager Icon", tint = PrimaryBlue)
                        Text("File Manager", color = PrimaryBlue)
                    }
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDim)
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compress Images ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(PurpleGradient)
                        )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.btn_compress),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { resetAll() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = PrimaryBlue)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Column {
                        Button(
                            onClick = { showFileSelectionOptions = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF))
                        ) {
                            Text(
                                if (selectedUris.isEmpty()) "📂 Select Files" else "📂 ${selectedUris.size} Files Selected",
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FormatBadge("Supported", listOf("JPG", "PDF"), Color(0xFF22C55E))
                        }
                    }
                }

                item {
                    Column {
                        PreviewCard(title = "Original", name = originalName, size = originalSize, uris = selectedUris, onMaximize = { if (selectedUris.isNotEmpty()) fullScreenUri = selectedUris[0] })

                        // Dropdown list for Batch Files
                        if (selectedUris.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { isFileListExpanded = !isFileListExpanded },
                                color = Color(0x0DFFFFFF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("View Selected Files (${selectedUris.size})", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Icon(
                                            imageVector = if (isFileListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextDim,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    AnimatedVisibility(visible = isFileListExpanded) {
                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                            selectedUris.forEach { uri ->
                                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(FileUtil.getFileName(context, uri), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Text(FileUtil.getFileSize(context, uri), color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(color = Color(0x0DFFFFFF), thickness = 0.5.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionLabel("Desired Maximum Size")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnitTab("KB", active = sizeUnit == "kb", modifier = Modifier.weight(1f)) { sizeUnit = "kb" }
                        UnitTab("MB", active = sizeUnit == "mb", modifier = Modifier.weight(1f)) { sizeUnit = "mb" }
                    }
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(value = targetSize, onValueChange = { targetSize = it }, placeholder = if (batchTargetMode == "total") "Total Combined Size" else "Eg: 500 KB or 2 MB", keyboardType = KeyboardType.Number)

                    // Batch Logic below desired input field
                    if (selectedUris.size > 1) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            SectionLabel("Batch Logic")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModeTab("Per Image", "Individual Target", active = batchTargetMode == "per_file", modifier = Modifier.weight(1f)) { batchTargetMode = "per_file" }
                                ModeTab("All Files", "Total Combined", active = batchTargetMode == "total", modifier = Modifier.weight(1f)) { batchTargetMode = "total" }
                            }
                            Text(
                                text = if (batchTargetMode == "per_file") "*Every image will be compressed to $targetSize $sizeUnit"
                                else "*All ${selectedUris.size} images will fit within $targetSize $sizeUnit in total",
                                color = TextDim.copy(0.6f), fontSize = 10.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                item {
                    SectionLabel("Compression Level")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeTab("Best", "Smooth", active = compressionMode == "standard", modifier = Modifier.weight(1f)) { compressionMode = "standard" }
                        ModeTab("Very Good", "Pixelated", active = compressionMode == "best", modifier = Modifier.weight(1f)) { compressionMode = "best" }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Compress & Change Format (Optional)", color = TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = enableCustomFormat,
                                onCheckedChange = { enableCustomFormat = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue
                                )
                            )
                        }
                        Text(text = "(This is for those users who want to compress images in different formats)", color = TextDim.copy(0.7f), fontSize = 11.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                if (enableCustomFormat) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FormatTab("JPG", active = targetFormat == "jpg", modifier = Modifier.weight(1f)) { targetFormat = "jpg" }
                                FormatTab("PDF", active = targetFormat == "pdf", modifier = Modifier.weight(1f)) { targetFormat = "pdf" }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (showPdfOptions) {
                    item {
                        SectionLabel("PDF Options")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeTab("Merge", "Combine All", active = pdfMode == "merge", modifier = Modifier.weight(1f)) { pdfMode = "merge" }
                            ModeTab("Separate", "Individual", active = pdfMode == "separate", modifier = Modifier.weight(1f)) { pdfMode = "separate" }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resize Dimensions (Optional)", color = TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = enableDimensions,
                            onCheckedChange = { enableDimensions = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryBlue
                            )
                        )
                    }
                }

                if (enableDimensions) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UnitTab("PX", active = dimUnit == "px", modifier = Modifier.weight(1f)) { dimUnit = "px" }
                                UnitTab("MM", active = dimUnit == "mm", modifier = Modifier.weight(1f)) { dimUnit = "mm" }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.weight(1f)) { DarkTextField(widthVal, { widthVal = it }, "Width", KeyboardType.Number) }
                                Box(modifier = Modifier.weight(1f)) { DarkTextField(heightVal, { heightVal = it }, "Height", KeyboardType.Number) }
                            }
                        }
                    }
                }

                item {
                    SectionLabel("Custom File Name (Optional)")
                    DarkTextField(value = customName, onValueChange = { customName = it }, placeholder = "Eg: profile_photo")
                }

                item {
                    val darkGreen = Color(0xFF064E3B)
                    Button(
                        onClick = {
                            if (selectedUris.isNotEmpty()) {
                                val fmt = if (enableCustomFormat) targetFormat else null
                                performCompression(fmt)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        enabled = !isProcessing && selectedUris.isNotEmpty()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isProcessing) Brush.linearGradient(listOf(darkGreen, darkGreen)) else Brush.linearGradient(GreenGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Start Compression", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isProcessing || statusMessage.startsWith("✅") || statusMessage.startsWith("❌")) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            StatusSection(
                                message = if (isProcessing) "$statusMessage ${(animatedProgress * 100).toInt()}%" else statusMessage,
                                isBlinking = isProcessing
                            )
                            if (isProcessing || animatedProgress > 0f) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).border(1.dp, Color(0x33FFFFFF), RectangleShape),
                                    color = Color(0xFF22C55E),
                                    trackColor = Color(0x33FFFFFF),
                                    strokeCap = StrokeCap.Butt
                                )
                            }
                        }
                    }
                }

                item {
                    PreviewCard(
                        title = "Compressed Preview",
                        name = if (compressedResults.size == 1) compressedResults[0].fileName else if (compressedResults.isNotEmpty()) "${compressedResults.size} Files Compressed" else "",
                        size = if (compressedResults.size == 1) FileUtil.formatSizeIndustry(compressedResults[0].sizeBytes) else if (compressedResults.isNotEmpty()) "Batch Complete" else "",
                        uris = compressedResults.mapNotNull { it.uri },
                        onMaximize = { if (compressedResults.isNotEmpty()) fullScreenUri = compressedResults[0].uri }
                    )
                }

                if (compressedResults.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Location")
                            compressedResults.forEach { res ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = res.fileName, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(text = "📁 Saved to: ${res.location}", color = TextDim, fontSize = 11.sp)
                                            Text(text = "⚖ Size: ${FileUtil.formatSizeIndustry(res.sizeBytes)}", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                            if (res.isBestEffort) {
                                                Text(text = "⚠️ Target unreachable. Best effort used.", color = Color(0xFFFCA5A5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { FileUtil.shareFile(context, res.uri) }) {
                                                Icon(Icons.Default.Share, contentDescription = "Share", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = { FileUtil.openFile(context, res.uri) }) {
                                                Text(text = "📁", fontSize = 20.sp)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = Color(0x0DFFFFFF), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (fullScreenUri != null) {
            Dialog(onDismissRequest = { fullScreenUri = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ZoomablePreviewImage(uri = fullScreenUri!!)
                    IconButton(onClick = { fullScreenUri = null }, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color.Black.copy(0.5f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePreviewImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                if (scale > 1f) { offset = androidx.compose.ui.geometry.Offset(offset.x + pan.x, offset.y + pan.y) }
                else { offset = androidx.compose.ui.geometry.Offset.Zero }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
    }
}

@Composable
fun FormatBadge(label: String, formats: List<String>, color: Color) {
    Column {
        Text(text = label, color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            formats.forEach { fmt ->
                Text(text = fmt, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
fun PreviewCard(title: String, name: String, size: String, uris: List<Uri>, onMaximize: () -> Unit) {
    val successGreen = Color(0xFF16A34A)
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceColor).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.2f)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onMaximize, modifier = Modifier.size(24.dp)) { Text(text = "⤢", color = TextDim, fontSize = 18.sp) }
        }
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF050B14)).padding(8.dp), contentAlignment = Alignment.Center) {
            if (uris.isEmpty()) { Text(text = "No Preview Available", color = TextDim.copy(0.3f), fontSize = 12.sp) }
            else if (uris.size < 3) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    uris.forEach { uri ->
                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.padding(horizontal = 4.dp).fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray).clickable { onMaximize() }, contentScale = ContentScale.Crop)
                    }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uris.take(12)) { uri ->
                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray).clickable { onMaximize() }, contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (name.isNotEmpty()) {
                Row {
                    Text(text = "Name: ", color = successGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = name, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (uris.isEmpty()) {
                Text(text = "No files selected", color = TextDim, fontSize = 12.sp)
            }
            if (size.isNotEmpty()) {
                Row {
                    Text(text = "Size: ", color = successGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = size, color = TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}