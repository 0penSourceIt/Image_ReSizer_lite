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
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.image.resizer.R
import com.image.resizer.logic.ConversionEngine
import com.image.resizer.logic.FileUtil
import com.image.resizer.logic.UnsupportedFormatException
import com.image.resizer.ui.components.DarkTextField
import com.image.resizer.ui.components.FormatTab
import com.image.resizer.ui.components.ModeTab
import com.image.resizer.ui.components.SectionLabel
import com.image.resizer.ui.components.StatusSection
import com.image.resizer.ui.theme.BlueGradient
import com.image.resizer.ui.theme.DarkBg
import com.image.resizer.ui.theme.GreenGradient
import com.image.resizer.ui.theme.PrimaryBlue
import com.image.resizer.ui.theme.SurfaceColor
import com.image.resizer.ui.theme.TextDim
import com.image.resizer.ui.theme.TextWhite
import kotlinx.coroutines.launch

// Models
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: String
)

data class ConversionResult(
    val fileName: String,
    val size: String,
    val location: String,
    val uri: Uri? = null
)

@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // States with Persistence
    var targetFormat by rememberSaveable { mutableStateOf("jpg") }
    var conversionMode by rememberSaveable { mutableStateOf("standard") } 
    var renameValue by rememberSaveable { mutableStateOf("") }
    var pdfMode by rememberSaveable { mutableStateOf("merge") } // merge or separate
    var isFileListExpanded by rememberSaveable { mutableStateOf(false) }

    // Custom Saver for Selected Files
    val selectedFilesSaver = listSaver<MutableState<List<SelectedFile>>, String>(
        save = { state -> state.value.map { "${it.uri}|${it.name}|${it.size}" } },
        restore = { list -> 
            mutableStateOf(list.map { 
                val parts = it.split("|")
                SelectedFile(parts[0].toUri(), parts[1], parts[2]) 
            })
        }
    )
    val selectedFilesState = rememberSaveable(saver = selectedFilesSaver) { mutableStateOf(emptyList()) }
    var selectedFiles by selectedFilesState

    // Custom Saver for Results
    val resultsSaver = listSaver<MutableState<List<ConversionResult>>, String>(
        save = { state -> state.value.map { "${it.fileName}|${it.size}|${it.location}|${it.uri ?: ""}" } },
        restore = { list ->
            mutableStateOf(list.map { 
                val parts = it.split("|")
                ConversionResult(parts[0], parts[1], parts[2], if(parts[3].isNotEmpty()) parts[3].toUri() else null)
            })
        }
    )
    val conversionResultsState = rememberSaveable(saver = resultsSaver) { mutableStateOf(emptyList()) }
    var conversionResults by conversionResultsState

    var isProcessing by rememberSaveable { mutableStateOf(false) }
    val processingProgressState = rememberSaveable { mutableFloatStateOf(0f) }
    var statusMessage by rememberSaveable { mutableStateOf("Ready to convert") }
    var fullScreenUri by remember { mutableStateOf<Uri?>(null) }
    
    // Dialog States for warnings and file selection
    var showWarningDialog by remember { mutableStateOf(false) }
    var warningTitle by remember { mutableStateOf("") }
    var warningMessage by remember { mutableStateOf("") }
    var currentActionFormat by remember { mutableStateOf("unknown") } 
    var showFileSelectionOptions by remember { mutableStateOf(false) }

    // Visibility logic for PDF Options: only show if multiple files are selected AND PDF is involved
    val showPdfOptions = remember(selectedFiles, targetFormat) {
        selectedFiles.size > 1 && (selectedFiles.any { it.name.lowercase().endsWith(".pdf") } || targetFormat == "pdf")
    }

    val hasConflict = remember(selectedFiles, targetFormat) {
        selectedFiles.any { it.name.lowercase().endsWith(targetFormat.lowercase()) || 
                (targetFormat == "jpg" && it.name.lowercase().endsWith("jpeg")) }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = processingProgressState.floatValue,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progress"
    )

    fun handleUrisSelected(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            selectedFiles = uris.map { uri ->
                SelectedFile(
                    uri = uri,
                    name = FileUtil.getFileName(context, uri),
                    size = FileUtil.getFileSize(context, uri)
                )
            }
            conversionResults = emptyList()
            processingProgressState.floatValue = 0f
            statusMessage = "Ready to convert"
            isFileListExpanded = false
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

    fun resetAppToReady() {
        selectedFiles = emptyList()
        conversionResults = emptyList()
        processingProgressState.floatValue = 0f
        statusMessage = "Ready to convert"
        isProcessing = false
        showWarningDialog = false
        isFileListExpanded = false
    }

    fun startConversionProcess(format: String) {
        scope.launch {
            try {
                isProcessing = true
                processingProgressState.floatValue = 0f
                val resultsList = mutableListOf<ConversionResult>()
                val isOriginal = conversionMode == "original"
                
                val total = selectedFiles.size
                selectedFiles.forEachIndexed { index, file ->
                    val outputs = ConversionEngine.compressAndSave(
                        context = context, 
                        uris = listOf(file.uri), 
                        targetSizeBytes = Long.MAX_VALUE, 
                        targetFormat = format, 
                        isHighMode = !isOriginal, 
                        customName = if (total > 1 && renameValue.isNotBlank()) "${renameValue}_${index+1}" else if (renameValue.isNotBlank()) renameValue else null,
                        isMergeMode = pdfMode == "merge"
                    ) { progress, message -> 
                        statusMessage = message
                        processingProgressState.floatValue = (index.toFloat() / total) + (progress / total)
                    }
                    
                    outputs.forEach { out -> 
                        resultsList.add(ConversionResult(out.fileName, FileUtil.formatSizeIndustry(out.sizeBytes), out.location, out.uri)) 
                    }
                }
                processingProgressState.floatValue = 1f
                conversionResults = resultsList
                isProcessing = false
                statusMessage = "✅ Conversion Complete"
            } catch (e: UnsupportedFormatException) {
                isProcessing = false
                warningTitle = "Unsupported Format: ${e.format.uppercase()}"
                warningMessage = e.reason + "\n\nWould you like to convert to JPEG instead, or cancel?"
                currentActionFormat = format 
                showWarningDialog = true
            } catch (e: Exception) {
                isProcessing = false
                statusMessage = "❌ Error: ${e.localizedMessage}"
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { resetAppToReady() },
            containerColor = SurfaceColor,
            title = { Text(warningTitle, color = TextWhite) },
            text = { Text(warningMessage, color = TextDim) },
            confirmButton = {
                if (currentActionFormat != "jpg") { 
                    Button(
                        onClick = { 
                            showWarningDialog = false
                            startConversionProcess("jpg")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Convert to JPEG")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { resetAppToReady() }) {
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
                        text = "Convert Images ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(BlueGradient)
                        )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.btn_convert),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { resetAppToReady() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = PrimaryBlue)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Button(
                        onClick = { showFileSelectionOptions = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF))
                    ) {
                        Text(
                            if (selectedFiles.isEmpty()) "📂 Select Files" else "📂 ${selectedFiles.size} Files Selected",
                            color = Color.White, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Column {
                        ConvertPreviewCard(
                            title = "Original Preview",
                            name = if (selectedFiles.size == 1) selectedFiles[0].name else if (selectedFiles.isNotEmpty()) "${selectedFiles.size} Files Selected" else "",
                            size = if (selectedFiles.size == 1) selectedFiles[0].size else if (selectedFiles.isNotEmpty()) "Batch Mode" else "",
                            uris = selectedFiles.map { it.uri },
                            onMaximize = { if (selectedFiles.isNotEmpty()) fullScreenUri = selectedFiles[0].uri }
                        )
                        
                        // Dropdown list for Batch Files
                        if (selectedFiles.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { isFileListExpanded = !isFileListExpanded },
                                color = Color(0x0DFFFFFF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("View Selected Files (${selectedFiles.size})", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Icon(
                                            imageVector = if (isFileListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextDim,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    AnimatedVisibility(visible = isFileListExpanded) {
                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                            selectedFiles.forEach { file ->
                                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(file.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Text(file.size, color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                if (hasConflict) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33EF4444))
                                .border(1.dp, Color(0x80EF4444), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "⚠️ Same format chosen. Change Target Format.",
                                color = Color(0xFFFCA5A5), fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                item {
                    SectionLabel("Target Format")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("jpg", "pdf").forEach { fmt ->
                                FormatTab(text = fmt.uppercase(), active = targetFormat == fmt, modifier = Modifier.weight(1f)) { targetFormat = fmt }
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
                    SectionLabel("Conversion Mode")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeTab("Standard", "Balanced", active = conversionMode == "standard", modifier = Modifier.weight(1f)) { conversionMode = "standard" }
                        ModeTab("Original", "Max Quality", active = conversionMode == "original", modifier = Modifier.weight(1f)) { conversionMode = "original" }
                    }
                }

                item {
                    SectionLabel("Rename Output (Optional)")
                    DarkTextField(value = renameValue, onValueChange = { renameValue = it }, placeholder = "Leave empty to auto-name")
                }

                item {
                    val darkGreen = Color(0xFF064E3B)
                    Button(
                        onClick = { 
                            if (selectedFiles.isNotEmpty()) {
                                startConversionProcess(targetFormat)
                            }
                        }, 
                        modifier = Modifier.fillMaxWidth().height(60.dp), 
                        shape = RoundedCornerShape(14.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent), 
                        contentPadding = PaddingValues(),
                        enabled = !isProcessing && selectedFiles.isNotEmpty()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isProcessing) Brush.linearGradient(listOf(darkGreen, darkGreen))
                                    else Brush.linearGradient(GreenGradient)
                                ), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Convert", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
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
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RectangleShape), 
                                color = PrimaryBlue, trackColor = Color(0x33FFFFFF),
                                strokeCap = StrokeCap.Butt
                            )
                        }
                    }
                }

                if (conversionResults.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionLabel("Converted Results")
                            conversionResults.forEach { res ->
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = res.fileName, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(text = "📁 Saved to: ${res.location}", color = TextDim, fontSize = 11.sp)
                                            Text(text = "⚖️ Size: ${res.size}", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            Dialog(onDismissRequest = { fullScreenUri = null }, properties = DialogProperties(usePlatformDefaultWidth = false) ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ZoomablePreviewImageContentFix(uri = fullScreenUri!!)
                    IconButton(onClick = { fullScreenUri = null }, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color.Black.copy(0.5f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePreviewImageContentFix(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale *= zoom
                scale = scale.coerceIn(1f, 5f)
                offset = if (scale > 1f) {
                    androidx.compose.ui.geometry.Offset(offset.x + pan.x, offset.y + pan.y)
                } else {
                    androidx.compose.ui.geometry.Offset.Zero
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
    }
}

@Composable
fun ConvertPreviewCard(title: String, name: String, size: String, uris: List<Uri>, onMaximize: () -> Unit) {
    val successGreen = Color(0xFF16A34A)
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceColor).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.2f)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onMaximize, modifier = Modifier.size(24.dp)) { Text(text = "⤢", color = TextDim, fontSize = 18.sp) }
        }
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF050B14)).padding(8.dp), contentAlignment = Alignment.Center) {
            if (uris.isEmpty()) { Text(text = "Preview Area", color = TextDim.copy(0.3f), fontSize = 12.sp) }
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