package dev.paulee.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.*
import dev.paulee.ui.*
import dev.paulee.ui.components.CustomInputDialog
import dev.paulee.ui.components.DialogType
import dev.paulee.ui.components.FileDialog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*


enum class DialogState {
    None, Add, Name, Import, Export,
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DataLoaderWindow(dataService: IDataService, dataDir: Path, onClose: (DataInfo?) -> Unit) {
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1100.dp, 700.dp)
    )

    var dialogState by remember { mutableStateOf(DialogState.None) }
    val sources = remember { mutableStateListOf<Source>() }
    val sourcePaths = remember { mutableStateListOf<Path>() }
    var selectedSource by remember { mutableStateOf<Source?>(null) }
    var dataInfoName by remember { mutableStateOf("") }

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
        onSelect: (Source) -> Unit,
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

    Window(state = windowState, onCloseRequest = { onClose(null) }, title = "Data Import") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                FieldTypeHelp(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.width(250.dp)) {
                        Text("Imported Sources", style = MaterialTheme.typography.h6)

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(
                                items = sources, key = { it.name }) { source ->
                                val bgColor = if (source == selectedSource) Color.LightGray else Color.Transparent

                                val hasSourceFile by derivedStateOf {
                                    sourcePaths.any { it.nameWithoutExtension == source.name }
                                }

                                var showPopup by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().background(bgColor)
                                        .clickable { selectedSource = source }.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(source.name)

                                    if (hasSourceFile) return@Row

                                    Box {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Info",
                                            tint = Color.Red,
                                            modifier = Modifier.pointerMoveFilter(
                                                onEnter = { showPopup = true; false },
                                                onExit = { showPopup = false; false })
                                        )

                                        if (showPopup) {
                                            SimplePopup(offset = IntOffset(170, 16)) {
                                                Text("Missing source file.", modifier = Modifier.padding(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { dialogState = DialogState.Add }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add")
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
                            Text("Remove")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { dialogState = DialogState.Export },
                            enabled = exportButtonEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                sources.clear()
                                sourcePaths.clear()
                                dialogState = DialogState.Import
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import")
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { dialogState = DialogState.Name },
                            enabled = loadButtonEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load")
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                        Text("Source Details", style = MaterialTheme.typography.h6)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedSource != null) {
                            Text("Source: ${selectedSource!!.name}", style = MaterialTheme.typography.subtitle1)

                            Spacer(modifier = Modifier.height(16.dp))

                            var selectedTab by remember { mutableStateOf(0) }
                            val tabs = listOf("Fields", "Filter", "Variant Mapping")

                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(4.dp)
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index

                                    Box(
                                        modifier = Modifier.weight(1f).background(
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        ).clickable { selectedTab = index }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.body1,
                                            color = if (isSelected) MaterialTheme.colors.primary else Color.Gray
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

                                            var linkSource by remember(
                                                selectedSource, field
                                            ) { mutableStateOf(field.sourceLink) }

                                            LaunchedEffect(
                                                variant, fieldType, uniqueIdentify, indexLang, indexDefault, linkSource
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
                                                                indexDefault
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
                                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F0F0))
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
                                                                "Field:",
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
                                                            Text("Variant: ", modifier = Modifier.width(70.dp))

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                RadioButton(
                                                                    selected = variant == "Basic",
                                                                    onClick = { variant = "Basic" })
                                                                Text("Basic")
                                                            }

                                                            if (fieldType == FieldType.INT) {
                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = variant == "Unique",
                                                                        onClick = { variant = "Unique" })
                                                                    Text("Unique")
                                                                }
                                                            }

                                                            if (fieldType == FieldType.TEXT) {
                                                                Spacer(modifier = Modifier.width(8.dp))

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = variant == "Index",
                                                                        onClick = { variant = "Index" })
                                                                    Text("Index")
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    if (variant == "Unique") {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(
                                                                checked = uniqueIdentify,
                                                                onCheckedChange = { uniqueIdentify = it })
                                                            Text("Identifiable")
                                                        }
                                                    }

                                                    if (variant == "Index") {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            var langExpanded by remember { mutableStateOf(false) }

                                                            Text("Language:")

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
                                                                onCheckedChange = { indexDefault = it })

                                                            Text("Default")
                                                        }
                                                    }

                                                    if (sources.size < 2) return@inner

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Link Source:", modifier = Modifier.width(100.dp))

                                                        var linkDropdownExpanded by remember { mutableStateOf(false) }

                                                        Box {
                                                            Button(onClick = { linkDropdownExpanded = true }) {
                                                                Text(linkSource.ifEmpty { "None" })
                                                            }

                                                            DropdownMenu(
                                                                expanded = linkDropdownExpanded,
                                                                onDismissRequest = { linkDropdownExpanded = false }) {
                                                                DropdownMenuItem(onClick = {
                                                                    linkSource = ""
                                                                    linkDropdownExpanded = false
                                                                }) {
                                                                    Text("None")
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
                                            Text("Filter Configuration", style = MaterialTheme.typography.h6)

                                            TextButton(
                                                onClick = {
                                                    pfKey = ""
                                                    pfLinkKey = ""
                                                    pfValue = ""
                                                },
                                                enabled = pfKey.isNotBlank() || pfLinkKey.isNotBlank() || pfValue.isNotBlank()
                                            ) {
                                                Text("Clear")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        SimpleTextField(
                                            textValue = pfKey,
                                            onTextValueChange = { pfKey = it },
                                            placeholderText = "Key",
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = pfLinkKey,
                                            onTextValueChange = { pfLinkKey = it },
                                            placeholderText = "Link Key",
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = pfValue,
                                            onTextValueChange = { pfValue = it },
                                            placeholderText = "Value",
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Hint(
                                            "Filters can be used to select objects matching a condition before searching with the initial query. They act on datasets, resembling a bridge between two data sets. Example: @source:key:value",
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
                                            Text("Variant Mapping Configuration", style = MaterialTheme.typography.h6)

                                            TextButton(
                                                onClick = {
                                                    vmBase = ""
                                                    vmVariantsText = ""
                                                }, enabled = vmBase.isNotBlank() || vmVariantsText.isNotBlank()
                                            ) {
                                                Text("Clear")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        SimpleTextField(
                                            textValue = vmBase,
                                            onTextValueChange = { vmBase = it },
                                            placeholderText = "Base Field",
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        SimpleTextField(
                                            textValue = vmVariantsText,
                                            onTextValueChange = { vmVariantsText = it },
                                            placeholderText = "Variants (comma-separated)",
                                            Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Hint(
                                            "Variant Mapping allows you to map variant field names to a base field. This is useful when you have multiple fields that represent the same data. Example: @source:key",
                                            Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else Text("Select a source to see its details.")
                    }
                }

                when (dialogState) {
                    DialogState.Name -> {
                        CustomInputDialog(
                            title = "Enter a name",
                            placeholder = "Name",
                            onDismissRequest = { dialogState = DialogState.None },
                            onConfirmClick = {
                                if (dataInfoName.isNotEmpty()) {
                                    dialogState = DialogState.None

                                    sourcePaths.forEach { path ->
                                        Files.copy(
                                            path, dataDir.resolve(path.name), StandardCopyOption.REPLACE_EXISTING
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
                                    sources.any { src -> src.name == path.nameWithoutExtension }
                                        .also { missing -> if (missing) sourcePaths.add(path) }
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
                                    }
                                }
                            }

                            dialogState = DialogState.None
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

private fun parseSourceFromFile(path: Path): Source? {
    if (path.extension != "csv") return null

    return path.bufferedReader().use { reader ->
        val header = runCatching { reader.readLine() }.getOrNull() ?: return null

        val values = runCatching { reader.readLine() }.getOrNull()?.split(",")

        val headerFields = header.split(",")
            .mapIndexed { idx, field -> BasicField(normalizeSourceName(field), getType(values?.get(idx))) }

        return@use Source(name = path.nameWithoutExtension, fields = headerFields)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FieldTypeHelp(modifier: Modifier = Modifier) {
    var showPopup by remember { mutableStateOf(false) }
    val maxNameLength = remember { FieldType.entries.maxOf { it.name.length } }

    Icon(
        imageVector = Icons.Filled.Info,
        contentDescription = "Field type help",
        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        modifier = modifier.size(24.dp)
            .pointerMoveFilter(onEnter = { showPopup = true; false }, onExit = { showPopup = false; false })
    )

    if (showPopup) {
        SimplePopup {
            Column(modifier = Modifier.padding(8.dp)) {
                FieldType.entries.forEach { type ->
                    Text(
                        text = "${
                            type.name.capitalize().padEnd(maxNameLength)
                        } - " + fieldTypeDescription(type), style = MaterialTheme.typography.body2.copy(
                            fontFamily = FontFamily.Monospace, letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }
    }
}

private fun fieldTypeDescription(fieldType: FieldType): String = when (fieldType) {
    FieldType.TEXT -> "Plain text such as names, sentences or plain characters"
    FieldType.INT -> "Whole number (e.g. 7, 42)"
    FieldType.FLOAT -> "Decimal number (e.g. 3.14)"
    FieldType.BOOLEAN -> "Logical values (true / false)"
}