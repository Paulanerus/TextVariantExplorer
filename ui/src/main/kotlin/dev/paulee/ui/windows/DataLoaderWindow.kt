package dev.paulee.ui.windows

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.*
import dev.paulee.api.internal.Embedding
import dev.paulee.ui.*
import dev.paulee.ui.components.CustomInputDialog
import dev.paulee.ui.components.DialogType
import dev.paulee.ui.components.FileDialog
import dev.paulee.ui.components.YesNoDialog
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

enum class DialogState {
    None, Add, Name, Import, Export, Missing
}

private enum class ModelDownloadStatus {
    Downloading, Completed, Cancelled, Failed
}

private data class ModelDownloadState(
    val progress: Int = 0,
    val status: ModelDownloadStatus = ModelDownloadStatus.Downloading,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DataLoaderWindow(dataService: IDataService, onClose: (DataInfo?) -> Unit) {
    val locale = LocalI18n.current

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1100.dp, 700.dp)
    )

    val scope = rememberCoroutineScope()

    var modelRefreshKey by remember { mutableStateOf(0) }
    val downloadStates = remember { mutableStateMapOf<Embedding.Model, ModelDownloadState>() }
    val downloadJobs = remember { mutableMapOf<Embedding.Model, Job>() }
    var downloadsExpanded by remember { mutableStateOf(true) }
    var pendingDownloadModels by remember { mutableStateOf<List<Embedding.Model>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose {
            downloadJobs.values.toList().forEach { it.cancel() }
        }
    }

    val availableModels by produceState(emptyList(), dataService, modelRefreshKey) {
        value = withContext(Dispatchers.IO) {
            Embedding.Model.entries.filter {
                dataService.modelDir().resolve(it.name).exists()
            }
        }
    }

    var dialogState by remember { mutableStateOf(DialogState.None) }
    val sources = remember { mutableStateListOf<Source>() }
    val sourcePaths = remember { mutableStateListOf<Path>() }
    var selectedSource by remember { mutableStateOf<Source?>(null) }
    var dataInfoName by remember { mutableStateOf("") }

    val missingSources = remember { mutableStateSetOf<String>() }
    val updatedSources = remember { mutableStateMapOf<String, List<BasicField>>() }

    val exportButtonEnabled by derivedStateOf { sources.isNotEmpty() }

    val loadButtonEnabled by derivedStateOf { sources.isNotEmpty() && sources.size == sourcePaths.size }

    val otherSources by derivedStateOf {
        selectedSource?.let { selected ->
            sources.filter { it.name != selected.name }
        } ?: emptyList()
    }

    fun updateSource(
        selectedSource: Source?,
        transform: (Source) -> Source,
        onSelect: (Source) -> Unit = {},
    ) {
        selectedSource?.let { current ->
            val updated = transform(current)

            if (updated != current) {
                val idx = sources.indexOfFirst { it.name == current.name }

                if (idx != -1) {
                    sources[idx] = updated
                    onSelect(updated)
                }
            }
        }
    }

    fun clearEmbeddingModelSelection(model: Embedding.Model) {
        var selectedUpdated = false

        sources.forEachIndexed { index, source ->
            val updatedFields = source.fields.map { field ->
                if (field is IndexField && field.embeddingModel == model) {
                    field.copy(embeddingModel = null)
                } else field
            }

            if (updatedFields != source.fields) {
                val updatedSource = source.copy(fields = updatedFields)
                sources[index] = updatedSource

                if (selectedSource?.name == updatedSource.name) {
                    selectedSource = updatedSource
                    selectedUpdated = true
                }
            }
        }

        if (!selectedUpdated && selectedSource != null) {
            val selected = sources.firstOrNull { it.name == selectedSource?.name }

            if (selected != null && selected != selectedSource)
                selectedSource = selected
        }
    }

    fun scheduleCleanup(model: Embedding.Model, expectedStatus: ModelDownloadStatus) {
        scope.launch {
            delay(2000)

            val current = downloadStates[model]

            if (current != null && current.status == expectedStatus)
                downloadStates.remove(model)
        }
    }

    fun startModelDownload(model: Embedding.Model) {
        val currentState = downloadStates[model]

        if (downloadJobs.containsKey(model) || currentState?.status == ModelDownloadStatus.Downloading) return

        downloadStates[model] = ModelDownloadState(progress = 0, status = ModelDownloadStatus.Downloading)
        downloadsExpanded = true

        val job = scope.launch {
            try {
                dataService.downloadModel(model, dataService.modelDir()) { progress ->
                    scope.launch {
                        val existing = downloadStates[model] ?: return@launch

                        if (existing.progress != progress)
                            downloadStates[model] = existing.copy(progress = progress)
                    }
                }

                downloadStates[model]?.let {
                    downloadStates[model] = it.copy(progress = 100, status = ModelDownloadStatus.Completed)
                }

                modelRefreshKey++

                scheduleCleanup(model, ModelDownloadStatus.Completed)
            } catch (_: CancellationException) {
                downloadStates[model]?.let {
                    downloadStates[model] = it.copy(status = ModelDownloadStatus.Cancelled)
                }

                clearEmbeddingModelSelection(model)

                scheduleCleanup(model, ModelDownloadStatus.Cancelled)
            } catch (_: Exception) {
                downloadStates[model]?.let {
                    downloadStates[model] = it.copy(status = ModelDownloadStatus.Failed)
                }

                clearEmbeddingModelSelection(model)

                scheduleCleanup(model, ModelDownloadStatus.Failed)
            } finally {
                downloadJobs.remove(model)
            }
        }

        downloadJobs[model] = job
    }

    Window(
        state = windowState,
        icon = App.icon,
        onCloseRequest = { onClose(null) },
        title = locale["data_loader.title"]
    ) {
        App.Theme.Current {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                val radioColors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colors.primary,
                    unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    disabledColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                )

                val checkboxColors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colors.primary,
                    uncheckedColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    checkmarkColor = MaterialTheme.colors.onPrimary,
                    disabledColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                    disabledIndeterminateColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                )

                FieldTypeHelp(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.width(250.dp)) {
                        Text(locale["data_loader.imported_sources"], style = MaterialTheme.typography.h6)

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(
                                items = sources, key = { it.name }) { source ->
                                val bgColor =
                                    if (source == selectedSource) MaterialTheme.colors.onSurface.copy(alpha = 0.14f) else Color.Transparent

                                val hasSourceFile by derivedStateOf {
                                    sourcePaths.any { it.nameWithoutExtension == source.name }
                                }

                                val hasMissingFields by derivedStateOf {
                                    missingSources.any { it == source.name }
                                }

                                var showPopupMissingFields by remember { mutableStateOf(false) }
                                var showPopupMissingSourceFile by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().background(bgColor)
                                        .clickable { selectedSource = source }.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(source.name)

                                    Box {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (hasMissingFields) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = locale["data_loader.warning"],
                                                    tint = Color.Yellow,
                                                    modifier = Modifier
                                                        .onPointerEvent(PointerEventType.Enter) {
                                                            showPopupMissingFields = true
                                                        }
                                                        .onPointerEvent(PointerEventType.Exit) {
                                                            showPopupMissingFields = false
                                                        }
                                                )
                                            }

                                            if (!hasSourceFile) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = locale["data_loader.info"],
                                                    tint = Color.Red,
                                                    modifier = Modifier
                                                        .onPointerEvent(PointerEventType.Enter) {
                                                            showPopupMissingSourceFile = true
                                                        }
                                                        .onPointerEvent(PointerEventType.Exit) {
                                                            showPopupMissingSourceFile = false
                                                        }
                                                )
                                            }
                                        }


                                        if (showPopupMissingSourceFile || showPopupMissingFields) {
                                            SimplePopup(offset = IntOffset(170, 16)) {
                                                Text(
                                                    if (showPopupMissingSourceFile) locale["data_loader.missing_source_file"] else locale["data_loader.missing_fields_hint"],
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val hasDownloads by remember {
                            derivedStateOf { downloadStates.isNotEmpty() }
                        }

                        AnimatedVisibility(
                            visible = hasDownloads,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut(animationSpec = tween(250)) + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colors.secondary, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        locale["data_loader.download_models.section"],
                                        style = MaterialTheme.typography.subtitle1
                                    )

                                    IconButton(onClick = { downloadsExpanded = !downloadsExpanded }) {
                                        Icon(
                                            imageVector = if (downloadsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = downloadsExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically()
                                ) {
                                    Column {
                                        downloadStates.entries
                                            .sortedBy { it.key.name }
                                            .forEach { (model, state) ->
                                                Column(
                                                    modifier = Modifier.fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(model.name, style = MaterialTheme.typography.body2)

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            when (state.status) {
                                                                ModelDownloadStatus.Downloading -> {
                                                                    Text(
                                                                        "${state.progress}%",
                                                                        style = MaterialTheme.typography.caption
                                                                    )

                                                                    IconButton(
                                                                        onClick = { downloadJobs[model]?.cancel() }
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Close,
                                                                            contentDescription = locale["input.cancel"]
                                                                        )
                                                                    }
                                                                }

                                                                ModelDownloadStatus.Completed -> {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colors.primary
                                                                    )
                                                                }

                                                                ModelDownloadStatus.Cancelled, ModelDownloadStatus.Failed -> {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Warning,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colors.error
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    val indicatorProgress = when (state.status) {
                                                        ModelDownloadStatus.Completed -> 1f
                                                        else -> (state.progress / 100f).coerceIn(0f, 1f)
                                                    }

                                                    LinearProgressIndicator(
                                                        progress = indicatorProgress,
                                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }

                        if (hasDownloads)
                            Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { dialogState = DialogState.Add }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(locale["data_loader.add"])
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val srcToRemove = selectedSource
                                srcToRemove?.let { src ->
                                    sources.remove(src)
                                    sourcePaths.removeIf { it.nameWithoutExtension == src.name }
                                }

                                selectedSource = null
                            }, enabled = selectedSource != null, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(locale["data_loader.remove"])
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { dialogState = DialogState.Export },
                            enabled = exportButtonEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(locale["data_loader.export"])
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                sources.clear()
                                sourcePaths.clear()
                                dialogState = DialogState.Import
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(locale["data_loader.import"])
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { dialogState = DialogState.Name },
                            enabled = loadButtonEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                locale["data_loader.load"]
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                        Text(locale["data_loader.source_details"], style = MaterialTheme.typography.h6)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedSource != null) {
                            Text(
                                locale["data_loader.source_label", selectedSource!!.name],
                                style = MaterialTheme.typography.subtitle1
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            var selectedTab by remember { mutableStateOf(0) }
                            val tabs = listOf(
                                locale["data_loader.tabs.fields"],
                                locale["data_loader.tabs.filter"],
                                locale["data_loader.tabs.variant_mapping"]
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colors.secondary, RoundedCornerShape(8.dp)).padding(4.dp)
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index

                                    Box(
                                        modifier = Modifier.weight(1f).background(
                                            color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        ).clickable { selectedTab = index }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.body1,
                                            color = if (isSelected) MaterialTheme.colors.onPrimary else Color.Gray
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            when (selectedTab) {
                                0 -> {
                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                        items(
                                            items = selectedSource?.fields ?: emptyList(),
                                            key = { "${selectedSource?.name}:${it.name}" }) { field ->
                                            val initialVariant = when (field) {
                                                is UniqueField -> "Unique"
                                                is IndexField -> "Index"
                                                else -> "Basic"
                                            }

                                            var variant by remember(selectedSource, field) {
                                                mutableStateOf(initialVariant)
                                            }

                                            var fieldType by remember(selectedSource, field) {
                                                mutableStateOf(field.fieldType)
                                            }

                                            var uniqueIdentify by remember(selectedSource, field) {
                                                mutableStateOf((field as? UniqueField)?.identify == true)
                                            }

                                            var indexLang by remember(selectedSource, field) {
                                                mutableStateOf((field as? IndexField)?.lang ?: Language.ENGLISH)
                                            }

                                            var indexDefault by remember(selectedSource, field) {
                                                mutableStateOf((field as? IndexField)?.default == true)
                                            }

                                            var embeddingModel by remember(selectedSource, field) {
                                                mutableStateOf((field as? IndexField)?.embeddingModel)
                                            }

                                            var linkSource by remember(
                                                selectedSource, field
                                            ) { mutableStateOf(field.sourceLink) }

                                            LaunchedEffect(
                                                variant,
                                                fieldType,
                                                uniqueIdentify,
                                                indexLang,
                                                indexDefault,
                                                linkSource,
                                                embeddingModel
                                            ) {
                                                if ((fieldType != FieldType.INT && variant == "Unique") || (fieldType != FieldType.TEXT && variant == "Index")) variant =
                                                    "Basic"

                                                val sourceContainingField = selectedSource

                                                sourceContainingField?.let { currentSource ->
                                                    val fieldIndex = currentSource.fields.indexOf(field)

                                                    if (fieldIndex != -1) {
                                                        val newField: SourceField = when (variant) {
                                                            "Unique" -> UniqueField(
                                                                field.name, fieldType, linkSource, uniqueIdentify
                                                            )

                                                            "Index" -> IndexField(
                                                                field.name,
                                                                fieldType,
                                                                linkSource,
                                                                indexLang,
                                                                indexDefault,
                                                                embeddingModel
                                                            )

                                                            else -> BasicField(field.name, fieldType, linkSource)
                                                        }

                                                        if (newField != currentSource.fields[fieldIndex]) {
                                                            val updatedFields =
                                                                currentSource.fields.toMutableList().apply {
                                                                    this[fieldIndex] = newField
                                                                }

                                                            val updatedSource =
                                                                currentSource.copy(fields = updatedFields)

                                                            val sourceIndex =
                                                                sources.indexOfFirst { it.name == currentSource.name }

                                                            if (sourceIndex != -1) {
                                                                sources[sourceIndex] = updatedSource

                                                                if (selectedSource?.name == updatedSource.name) selectedSource =
                                                                    updatedSource
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Box(
                                                modifier = Modifier.fillMaxWidth().background(
                                                    MaterialTheme.colors.secondary,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                    .padding(horizontal = 8.dp)
                                            ) {
                                                Column inner@{
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        var typeExpanded by remember { mutableStateOf(false) }

                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                locale["data_loader.field.label"],
                                                                style = MaterialTheme.typography.subtitle1,

                                                                )

                                                            SelectionContainer {
                                                                Text(
                                                                    field.name,
                                                                    style = MaterialTheme.typography.subtitle1,
                                                                )
                                                            }
                                                        }

                                                        Box {
                                                            Button(onClick = { typeExpanded = true }) {
                                                                Text(fieldType.name.capitalize())
                                                            }

                                                            DropdownMenu(
                                                                expanded = typeExpanded,
                                                                onDismissRequest = { typeExpanded = false }) {
                                                                FieldType.entries.forEach { type ->
                                                                    DropdownMenuItem(onClick = {
                                                                        fieldType = type
                                                                        typeExpanded = false
                                                                    }) {
                                                                        Text(
                                                                            type.name.capitalize()
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (fieldType == FieldType.INT || fieldType == FieldType.TEXT) {
                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                "${locale["data_loader.variant.label"]} ",
                                                                modifier = Modifier.width(70.dp)
                                                            )

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                RadioButton(
                                                                    selected = variant == "Basic",
                                                                    colors = radioColors,
                                                                    onClick = { variant = "Basic" })
                                                                Text(locale["data_loader.variant.basic"])
                                                            }

                                                            if (fieldType == FieldType.INT) {
                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = variant == "Unique",
                                                                        colors = radioColors,
                                                                        onClick = { variant = "Unique" })
                                                                    Text(locale["data_loader.variant.unique"])
                                                                }
                                                            }

                                                            if (fieldType == FieldType.TEXT) {
                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = variant == "Index",
                                                                        colors = radioColors,
                                                                        onClick = { variant = "Index" })
                                                                    Text(locale["data_loader.variant.index"])
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    if (variant == "Unique") {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(
                                                                checked = uniqueIdentify,
                                                                colors = checkboxColors,
                                                                onCheckedChange = { uniqueIdentify = it })
                                                            Text(locale["data_loader.unique.identifiable"])
                                                        }
                                                    }

                                                    if (variant == "Index") {
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                var langExpanded by remember { mutableStateOf(false) }

                                                                Text(locale["data_loader.index.language"])

                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Box {
                                                                    Button(onClick = { langExpanded = true }) {
                                                                        Text(indexLang.name)
                                                                    }

                                                                    DropdownMenu(
                                                                        expanded = langExpanded,
                                                                        onDismissRequest = { langExpanded = false }) {
                                                                        Language.entries.forEach { lang ->
                                                                            DropdownMenuItem(onClick = {
                                                                                indexLang = lang
                                                                                langExpanded = false
                                                                            }) {
                                                                                Text(lang.name)
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Checkbox(
                                                                    checked = indexDefault,
                                                                    colors = checkboxColors,
                                                                    onCheckedChange = { indexDefault = it })

                                                                Text(locale["data_loader.default"])

                                                            }

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                var embeddingExpanded by remember { mutableStateOf(false) }

                                                                Text(locale["data_loader.index.embedding"])

                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Box {
                                                                    Button(onClick = { embeddingExpanded = true }) {
                                                                        val selectedModelStatus =
                                                                            embeddingModel?.let { downloadStates[it]?.status }

                                                                        val text = buildString {
                                                                            append(
                                                                                embeddingModel?.name
                                                                                    ?: locale["data_loader.index.embedding.no"]
                                                                            )

                                                                            if (selectedModelStatus == ModelDownloadStatus.Downloading)
                                                                                append(" (${locale["data_loader.index.embedding.downloading"]})")
                                                                        }

                                                                        Text(text)
                                                                    }

                                                                    DropdownMenu(
                                                                        expanded = embeddingExpanded,
                                                                        onDismissRequest = {
                                                                            embeddingExpanded = false
                                                                        }) {
                                                                        DropdownMenuItem(onClick = {
                                                                            embeddingModel = null
                                                                            embeddingExpanded = false
                                                                        }) {
                                                                            Text(locale["data_loader.index.embedding.no"])
                                                                        }

                                                                        availableModels.forEach { model ->
                                                                            DropdownMenuItem(onClick = {
                                                                                embeddingModel = model
                                                                                embeddingExpanded = false
                                                                            }) {
                                                                                val text = buildString {
                                                                                    append(model.name)

                                                                                    if (downloadStates[model]?.status == ModelDownloadStatus.Downloading)
                                                                                        append(" (${locale["data_loader.index.embedding.downloading"]})")
                                                                                }

                                                                                Text(text)
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                            }

                                                            Hint(
                                                                locale["data_loader.index.embedding.hint"],
                                                                Modifier.fillMaxWidth(),
                                                                true
                                                            )
                                                        }
                                                    }

                                                    if (sources.size < 2) return@inner

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            locale["data_loader.link_source"],
                                                            modifier = Modifier.width(100.dp)
                                                        )

                                                        var linkDropdownExpanded by remember { mutableStateOf(false) }

                                                        Box {
                                                            Button(onClick = { linkDropdownExpanded = true }) {
                                                                Text(linkSource.ifEmpty { locale["data_loader.none"] })
                                                            }

                                                            DropdownMenu(
                                                                expanded = linkDropdownExpanded,
                                                                onDismissRequest = { linkDropdownExpanded = false }) {
                                                                DropdownMenuItem(onClick = {
                                                                    linkSource = ""
                                                                    linkDropdownExpanded = false
                                                                }) {
                                                                    Text(locale["data_loader.none"])
                                                                }

                                                                otherSources.forEach { source ->
                                                                    DropdownMenuItem(onClick = {
                                                                        linkSource = source.name
                                                                        linkDropdownExpanded = false
                                                                    }) {
                                                                        Text(source.name)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }

                                1 -> {
                                    val preFilter = selectedSource!!.preFilter

                                    var pfKey by remember(selectedSource) {
                                        mutableStateOf(preFilter?.key ?: "")
                                    }

                                    var pfLinkKey by remember(selectedSource) {
                                        mutableStateOf(preFilter?.linkKey ?: "")
                                    }

                                    var pfValue by remember(selectedSource) {
                                        mutableStateOf(preFilter?.value ?: "")
                                    }

                                    LaunchedEffect(pfKey, pfLinkKey, pfValue) {
                                        val updatedPreFilter =
                                            if (pfKey.isNotBlank() || pfLinkKey.isNotBlank() || pfValue.isNotBlank()) {
                                                PreFilter(pfKey, pfLinkKey, pfValue)
                                            } else {
                                                null
                                            }

                                        updateSource(
                                            selectedSource,
                                            transform = { it.copy(preFilter = updatedPreFilter) },
                                            onSelect = { selectedSource = it })
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                locale["data_loader.filter.config"],
                                                style = MaterialTheme.typography.h6
                                            )

                                            TextButton(
                                                onClick = {
                                                    pfKey = ""
                                                    pfLinkKey = ""
                                                    pfValue = ""
                                                },
                                                enabled = pfKey.isNotBlank() || pfLinkKey.isNotBlank() || pfValue.isNotBlank()
                                            ) {
                                                Text(locale["data_loader.clear"])
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        SimpleTextField(
                                            textValue = pfKey,
                                            onTextValueChange = { pfKey = it },
                                            placeholderText = locale["data_loader.filter.key"],
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = pfLinkKey,
                                            onTextValueChange = { pfLinkKey = it },
                                            placeholderText = locale["data_loader.filter.link_key"],
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = pfValue,
                                            onTextValueChange = { pfValue = it },
                                            placeholderText = locale["data_loader.filter.value"],
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Hint(
                                            locale["data_loader.filter.hint"],
                                            Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                2 -> {
                                    val variantMapping = selectedSource!!.variantMapping

                                    var vmBase by remember(selectedSource) {
                                        mutableStateOf(variantMapping?.base ?: "")
                                    }

                                    var vmVariantsText by remember(selectedSource) {
                                        mutableStateOf(variantMapping?.variants?.joinToString(", ") ?: "")
                                    }

                                    LaunchedEffect(vmBase, vmVariantsText) {
                                        val variants =
                                            vmVariantsText.split(",").map { it.trim() }.filter { it.isNotBlank() }

                                        val updatedMapping = if (vmBase.isNotBlank() && variants.isNotEmpty()) {
                                            VariantMapping(vmBase, variants)
                                        } else {
                                            null
                                        }

                                        updateSource(
                                            selectedSource,
                                            transform = { it.copy(variantMapping = updatedMapping) },
                                            onSelect = { selectedSource = it })
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(locale["data_loader.vm.config"], style = MaterialTheme.typography.h6)

                                            TextButton(
                                                onClick = {
                                                    vmBase = ""
                                                    vmVariantsText = ""
                                                }, enabled = vmBase.isNotBlank() || vmVariantsText.isNotBlank()
                                            ) {
                                                Text(locale["data_loader.clear"])
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        SimpleTextField(
                                            textValue = vmBase,
                                            onTextValueChange = { vmBase = it },
                                            placeholderText = locale["data_loader.vm.base_field"],
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = vmVariantsText,
                                            onTextValueChange = { vmVariantsText = it },
                                            placeholderText = locale["data_loader.vm.variants_placeholder"],
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Hint(
                                            locale["data_loader.vm.hint"],
                                            Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else Text(locale["data_loader.select_source_hint"])
                    }
                }

                when (dialogState) {
                    DialogState.Missing -> {
                        YesNoDialog(
                            title = locale["data_loader.missing_values.title"],
                            text = locale["data_loader.missing_values.text"],
                            onDismissRequest = {
                                dialogState = DialogState.None
                                updatedSources.clear()
                            }) { value ->

                            if (!value) return@YesNoDialog

                            sources.forEach {
                                val missing = updatedSources[it.name] ?: return@forEach

                                updateSource(
                                    it,
                                    transform = { src ->
                                        src.copy(fields = src.fields + missing)
                                    }
                                )
                            }
                        }
                    }

                    DialogState.Name -> {
                        CustomInputDialog(
                            title = locale["data_loader.name_dialog.title"],
                            placeholder = locale["data_loader.name_dialog.placeholder"],
                            onDismissRequest = { dialogState = DialogState.None },
                            onConfirmClick = {
                                if (dataInfoName.isNotEmpty()) {
                                    dialogState = DialogState.None

                                    val dataPath = dataService.dataDir()
                                        .resolve(dataInfoName).resolve("data")

                                    if (dataPath.notExists())
                                        dataPath.createDirectories()

                                    sourcePaths.forEach { path ->
                                        Files.copy(
                                            path,
                                            dataPath.resolve(path.name),
                                            StandardCopyOption.REPLACE_EXISTING
                                        )
                                    }
                                    onClose(DataInfo(dataInfoName, sources.toList()))
                                }
                            },
                            textFieldValue = dataInfoName,
                            onTextFieldValueChange = { dataInfoName = it })
                    }

                    DialogState.Add, DialogState.Import -> {
                        FileDialog(extension = if (dialogState == DialogState.Add) "csv" else "json") {

                            if (dialogState == DialogState.Add) {
                                val newSources = it.filterNot { path ->
                                    val pathName = path.nameWithoutExtension

                                    sources.any { src -> src.name == pathName }
                                        .also { missing ->

                                            if (missing) {
                                                val source =
                                                    sources.firstOrNull { src -> src.name == pathName }
                                                        ?: return@also

                                                val fields = readHeader(path)

                                                val fileNames = fields.map { f -> f.name }
                                                val sourceNames = source.fields.map { f -> f.name }

                                                val hasMissingFields = source.fields.any { f -> f.name !in fileNames }

                                                if (hasMissingFields) missingSources.add(source.name)

                                                val newFields = fields.filter { f -> f.name !in sourceNames }

                                                if (newFields.isNotEmpty()) updatedSources[source.name] = newFields

                                                sourcePaths.add(path)
                                            }
                                        }
                                }.mapNotNull { path ->
                                    val source = parseSourceFromFile(path) ?: return@mapNotNull null
                                    sourcePaths.add(path)
                                    source
                                }

                                sources.addAll(newSources)
                            } else {
                                it.firstOrNull()?.readText()?.let { text ->
                                    val dataInfo = dataService.dataInfoFromString(text)

                                    if (dataInfo != null) {
                                        sources.addAll(dataInfo.sources)
                                        dataInfoName = dataInfo.name

                                        val referencedModels = dataInfo.sources
                                            .flatMap { source -> source.fields }
                                            .mapNotNull { field -> (field as? IndexField)?.embeddingModel }
                                            .distinct()

                                        if (referencedModels.isNotEmpty()) {
                                            val installedModels = availableModels.toMutableSet().apply {
                                                downloadStates.filter { entry -> entry.value.status == ModelDownloadStatus.Completed }
                                                    .forEach { entry -> add(entry.key) }
                                            }

                                            val activeDownloads = downloadStates
                                                .filter { entry -> entry.value.status == ModelDownloadStatus.Downloading }
                                                .keys

                                            val missingModels = referencedModels.filter { model ->
                                                model !in installedModels && model !in activeDownloads
                                            }

                                            if (missingModels.isNotEmpty())
                                                pendingDownloadModels = missingModels
                                        }
                                    }
                                }
                            }

                            dialogState = if (updatedSources.isNotEmpty()) DialogState.Missing else DialogState.None
                        }
                    }

                    DialogState.Export -> {
                        FileDialog(dialogType = DialogType.SAVE, extension = "json") {
                            if (it.isNotEmpty()) {
                                val savePath = it.first()

                                val dataInfo = DataInfo(savePath.nameWithoutExtension, sources.toList())

                                dataService.dataInfoToString(dataInfo)?.let { str -> savePath.writeText(str) }
                            }

                            dialogState = DialogState.None
                        }
                    }

                    else -> {}
                }

                if (pendingDownloadModels.isNotEmpty()) {
                    val modelsToDownload = pendingDownloadModels
                    val modelNames = modelsToDownload.joinToString("\n", postfix = "\n") { "- ${it.name}" }

                    YesNoDialog(
                        title = locale["data_loader.download_models.title"],
                        text = locale["data_loader.download_models.text", modelNames],
                        onDismissRequest = { pendingDownloadModels = emptyList() }
                    ) { confirm ->
                        if (confirm)
                            modelsToDownload.forEach { startModelDownload(it) }

                        pendingDownloadModels = emptyList()
                    }
                }
            }
        }
    }
}

private fun getType(value: String?): FieldType {
    if (value == null) return FieldType.TEXT

    value.toIntOrNull()?.let { return FieldType.INT }
    value.toFloatOrNull()?.let { return FieldType.FLOAT }
    value.lowercase().toBooleanStrictOrNull()?.let { return FieldType.BOOLEAN }

    return FieldType.TEXT
}

private fun readHeader(path: Path): List<BasicField> {
    if (path.extension != "csv") return emptyList()

    val (headerLine, sampleLine) = path.bufferedReader().useLines { lines ->
        val it = lines.iterator()
        val header = if (it.hasNext()) it.next() else return emptyList()
        val sample = if (it.hasNext()) it.next() else null
        header to sample
    }

    val headers = headerLine.split(',')
    val samples = sampleLine?.split(',')

    return headers.mapIndexed { idx, raw ->
        BasicField(
            normalizeSourceName(raw),
            getType(samples?.getOrNull(idx))
        )
    }
}

private fun parseSourceFromFile(path: Path): Source? {
    val headerFields = readHeader(path)

    return if (headerFields.isEmpty()) null else Source(name = path.nameWithoutExtension, fields = headerFields)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FieldTypeHelp(modifier: Modifier = Modifier) {
    val locale = LocalI18n.current

    var showPopup by remember { mutableStateOf(false) }
    val maxNameLength = remember { FieldType.entries.maxOf { it.name.length } }

    Icon(
        imageVector = Icons.Filled.Info,
        contentDescription = locale["data_loader.field_type_help"],
        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        modifier = modifier.size(24.dp)
            .onPointerEvent(PointerEventType.Enter) { showPopup = true }
            .onPointerEvent(PointerEventType.Exit) { showPopup = false }
    )

    if (showPopup) {
        SimplePopup {
            Column(modifier = Modifier.padding(8.dp)) {
                FieldType.entries.forEach { type ->
                    Text(
                        text = "${
                            type.name.capitalize().padEnd(maxNameLength)
                        } - " + fieldTypeDescription(type, locale), style = MaterialTheme.typography.body2.copy(
                            fontFamily = FontFamily.Monospace, letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }
    }
}

private fun fieldTypeDescription(fieldType: FieldType, locale: I18n): String = when (fieldType) {
    FieldType.TEXT -> locale["data_loader.field_type.TEXT"]
    FieldType.INT -> locale["data_loader.field_type.INT"]
    FieldType.FLOAT -> locale["data_loader.field_type.FLOAT"]
    FieldType.BOOLEAN -> locale["data_loader.field_type.BOOLEAN"]
}