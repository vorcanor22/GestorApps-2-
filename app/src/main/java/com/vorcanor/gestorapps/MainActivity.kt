package com.vorcanor.gestorapps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// COLORES
// ─────────────────────────────────────────────────────────────

private val Bg = Color(0xFF0B0C11)
private val Surface1 = Color(0xFF151720)
private val Surface2 = Color(0xFF20222D)
private val Purple = Color(0xFF9B7BFF)
private val PurpleSoft = Color(0xFFC4B3FF)
private val Green = Color(0xFF5CDBA0)
private val Blue = Color(0xFF6CB8FF)
private val Orange = Color(0xFFFFB86C)
private val Red = Color(0xFFFF727D)
private val MainText = Color(0xFFF4F3FA)
private val Muted = Color(0xFF9396A8)

// ─────────────────────────────────────────────────────────────
// MODELOS
// ─────────────────────────────────────────────────────────────

data class InstalledApp(
    val name: String,
    val packageName: String,
    val version: String,
    val isSystem: Boolean,
    val icon: Drawable
)

data class DeviceStorage(
    val total: Long,
    val used: Long,
    val free: Long
)

data class SelectedApk(
    val uri: Uri,
    val name: String,
    val packageName: String,
    val version: String,
    val size: Long,
    val icon: Drawable?
)

// ─────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GestorAppsRoot()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// APLICACIÓN PRINCIPAL
// ─────────────────────────────────────────────────────────────

@Composable
fun GestorAppsRoot() {
    val context = LocalContext.current
    val pm = context.packageManager

    var currentTab by remember { mutableIntStateOf(0) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var deviceStorage by remember { mutableStateOf<DeviceStorage?>(null) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedApk by remember { mutableStateOf<SelectedApk?>(null) }
    var searchText by remember { mutableStateOf("") }
    var appFilter by remember { mutableStateOf("Todas") }
    var sortAZ by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf("") }

    fun refreshData() {
        loading = true

        try {
            val apps = pm.getInstalledApplications(0).mapNotNull { info ->
                try {
                    val packageInfo = pm.getPackageInfo(info.packageName, 0)
                    InstalledApp(
                        name = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        version = packageInfo.versionName ?: "Desconocida",
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                        icon = pm.getApplicationIcon(info)
                    )
                } catch (_: Exception) {
                    null
                }
            }

            installedApps = apps.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } catch (_: Exception) {
            installedApps = emptyList()
        }

        try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            deviceStorage = DeviceStorage(
                total = total,
                used = (total - free).coerceAtLeast(0),
                free = free
            )
        } catch (_: Exception) {
            deviceStorage = null
        }

        lastRefresh = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        loading = false
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val filteredApps = installedApps
        .filter { app ->
            val searchMatches = app.name.contains(searchText, ignoreCase = true) ||
                    app.packageName.contains(searchText, ignoreCase = true)

            val filterMatches = when (appFilter) {
                "Usuario" -> !app.isSystem
                "Sistema" -> app.isSystem
                else -> true
            }

            searchMatches && filterMatches
        }
        .let { list ->
            if (sortAZ) list.sortedBy { it.name.lowercase(Locale.getDefault()) }
            else list.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
        }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "gestorapps_selected.apk")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val archiveInfo = pm.getPackageArchiveInfo(
                    tempFile.absolutePath,
                    PackageManager.GET_ACTIVITIES
                )

                if (archiveInfo != null) {
                    val archiveAppInfo = archiveInfo.applicationInfo
                    archiveAppInfo?.sourceDir = tempFile.absolutePath
                    archiveAppInfo?.publicSourceDir = tempFile.absolutePath

                    val appLabel = if (archiveAppInfo != null) {
                        pm.getApplicationLabel(archiveAppInfo).toString()
                    } else {
                        tempFile.name
                    }

                    val apkIcon = try {
                        if (archiveAppInfo != null) pm.getApplicationIcon(archiveAppInfo)
                        else null
                    } catch (_: Exception) {
                        null
                    }

                    selectedApk = SelectedApk(
                        uri = uri,
                        name = appLabel,
                        packageName = archiveInfo.packageName ?: "Desconocido",
                        version = archiveInfo.versionName ?: "Desconocida",
                        size = tempFile.length(),
                        icon = apkIcon
                    )
                } else {
                    selectedApk = SelectedApk(
                        uri = uri,
                        name = tempFile.name,
                        packageName = "No reconocido",
                        version = "No disponible",
                        size = tempFile.length(),
                        icon = null
                    )
                }
            } catch (_: Exception) {
                selectedApk = null
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            secondary = PurpleSoft,
            background = Bg,
            surface = Surface1
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(
                    containerColor = Surface1,
                    contentColor = MainText,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = {
                            Icon(Icons.Default.DonutLarge, contentDescription = null)
                        },
                        label = { Text("Espacio") },
                        colors = navColors()
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = {
                            Icon(Icons.Default.Apps, contentDescription = null)
                        },
                        label = { Text("Aplicaciones") },
                        colors = navColors()
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        label = { Text("Paquetes") },
                        colors = navColors()
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (currentTab) {
                    0 -> StorageTab(
                        storage = deviceStorage,
                        appCount = installedApps.size,
                        loading = loading,
                        lastRefresh = lastRefresh,
                        onRefresh = { refreshData() }
                    )

                    1 -> ApplicationsTab(
                        apps = filteredApps,
                        total = installedApps.size,
                        search = searchText,
                        onSearch = { searchText = it },
                        filter = appFilter,
                        onFilter = { appFilter = it },
                        sortAZ = sortAZ,
                        onSort = { sortAZ = !sortAZ },
                        onRefresh = { refreshData() },
                        lastRefresh = lastRefresh,
                        onAppClick = { selectedApp = it }
                    )

                    2 -> PackagesTab(
                        selectedApk = selectedApk,
                        onSelectApk = {
                            apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                        },
                        onRemoveApk = { selectedApk = null },
                        onInstall = {
                            selectedApk?.let { item ->
                                try {
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            item.uri,
                                            "application/vnd.android.package-archive"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(installIntent)
                                } catch (_: Exception) {
                                }
                            }
                        },
                        installedApps = installedApps.size,
                        onRefresh = { refreshData() }
                    )
                }
            }
        }

        selectedApp?.let { app ->
            ApplicationDetailsDialog(
                app = app,
                onDismiss = { selectedApp = null },
                onOpen = {
                    try {
                        pm.getLaunchIntentForPackage(app.packageName)?.let {
                            context.startActivity(it)
                        }
                    } catch (_: Exception) {
                    }
                },
                onAndroidSettings = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            )
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = PurpleSoft,
    selectedTextColor = PurpleSoft,
    indicatorColor = Color(0xFF30264A),
    unselectedIconColor = Muted,
    unselectedTextColor = Muted
)

// ─────────────────────────────────────────────────────────────
// PANTALLA: ALMACENAMIENTO
// ─────────────────────────────────────────────────────────────

@Composable
fun StorageTab(
    storage: DeviceStorage?,
    appCount: Int,
    loading: Boolean,
    lastRefresh: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(
            eyebrow = "VORCANOR",
            title = "Almacenamiento",
            onRefresh = onRefresh
        )

        if (storage == null) {
            EmptyStorage(loading)
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                StorageRingCard(storage)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageStat(
                        title = "Ocupado",
                        value = formatBytes(storage.used),
                        color = Purple,
                        modifier = Modifier.weight(1f)
                    )
                    StorageStat(
                        title = "Disponible",
                        value = formatBytes(storage.free),
                        color = Green,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionTitle("Uso del dispositivo", "Resumen general")
            }

            item {
                StorageCategory(
                    title = "Aplicaciones",
                    subtitle = "$appCount aplicaciones instaladas",
                    value = null,
                    icon = Icons.Default.Apps,
                    color = Purple
                )
            }

            item {
                StorageCategory(
                    title = "Imágenes y vídeos",
                    subtitle = "Categoría disponible en el sistema",
                    value = null,
                    icon = Icons.Default.Image,
                    color = Blue
                )
            }

            item {
                StorageCategory(
                    title = "Audio",
                    subtitle = "Música y archivos de sonido",
                    value = null,
                    icon = Icons.Default.MusicNote,
                    color = Orange
                )
            }

            item {
                StorageCategory(
                    title = "Documentos y descargas",
                    subtitle = "Archivos guardados en el dispositivo",
                    value = null,
                    icon = Icons.Default.Description,
                    color = Green
                )
            }

            item {
                StorageCategory(
                    title = "Otros",
                    subtitle = "Sistema, datos y contenido no clasificado",
                    value = null,
                    icon = Icons.Default.Folder,
                    color = Red
                )
            }

            item {
                InfoNote(
                    "El almacenamiento total, ocupado y libre es real. " +
                        "El desglose por categorías no está disponible con precisión " +
                        "mediante los permisos actuales de Android."
                )
            }

            if (lastRefresh.isNotBlank()) {
                item {
                    Text(
                        "Última actualización: $lastRefresh",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StorageRingCard(storage: DeviceStorage) {
    val percentage = if (storage.total > 0) {
        (storage.used.toFloat() / storage.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        label = "storage_ring"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(230.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Surface2,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 22.dp.toPx())
                    )

                    drawArc(
                        color = Purple,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedPercentage,
                        useCenter = false,
                        style = Stroke(width = 22.dp.toPx())
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(percentage * 100).toInt()}%",
                        color = MainText,
                        fontSize = 43.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("utilizado", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${formatBytes(storage.used)} de ${formatBytes(storage.total)}",
                        color = PurpleSoft,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Almacenamiento interno",
                color = MainText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${formatBytes(storage.free)} libres",
                color = Green,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun StorageStat(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(7.dp))
                Text(title, color = Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                value,
                color = MainText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StorageCategory(
    title: String,
    subtitle: String,
    value: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(title, color = MainText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }

            if (value != null) {
                Text(value, color = MainText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyStorage(loading: Boolean) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = Purple)
        } else {
            Text("No se pudo leer el almacenamiento", color = Muted)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PANTALLA: APLICACIONES
// ─────────────────────────────────────────────────────────────

@Composable
fun ApplicationsTab(
    apps: List<InstalledApp>,
    total: Int,
    search: String,
    onSearch: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    sortAZ: Boolean,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    lastRefresh: String,
    onAppClick: (InstalledApp) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 18.dp)
    ) {
        ScreenHeader(
            eyebrow = "VORCANOR",
            title = "Aplicaciones",
            onRefresh = onRefresh
        )

        Text(
            "$total aplicaciones instaladas",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar aplicaciones", color = Muted) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = PurpleSoft)
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Default.Close, "Limpiar", tint = Muted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(17.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = Surface2,
                focusedTextColor = MainText,
                unfocusedTextColor = MainText,
                cursorColor = Purple
            )
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Todas", "Usuario", "Sistema").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilter(option) },
                        label = { Text(option, fontSize = 12.sp) },
                        shape = RoundedCornerShape(13.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Purple,
                            selectedLabelColor = Color.White,
                            containerColor = Surface1,
                            labelColor = Muted
                        )
                    )
                }
            }

            IconButton(onClick = onSort) {
                Icon(
                    if (sortAZ) Icons.Default.SortByAlpha else Icons.Default.Sort,
                    contentDescription = "Cambiar orden",
                    tint = PurpleSoft
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null, tint = Muted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No se encontraron aplicaciones", color = Muted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    ApplicationRow(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
fun ApplicationRow(
    app: InstalledApp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableIcon(app.icon, Modifier.size(48.dp))

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    app.name,
                    color = MainText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    if (app.isSystem) "Aplicación del sistema" else "Aplicación de usuario",
                    color = Muted,
                    fontSize = 11.sp
                )

                Text(
                    "Versión ${app.version}",
                    color = PurpleSoft,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PANTALLA: PAQUETES APK
// ─────────────────────────────────────────────────────────────

@Composable
fun PackagesTab(
    selectedApk: SelectedApk?,
    onSelectApk: () -> Unit,
    onRemoveApk: () -> Unit,
    onInstall: () -> Unit,
    installedApps: Int,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(
            eyebrow = "VORCANOR",
            title = "Paquetes APK",
            onRefresh = onRefresh
        )

        Text(
            "Analiza archivos APK e inicia su instalación.",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(23.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Column(Modifier.padding(19.dp)) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Android, null, tint = PurpleSoft, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Instalador de paquetes",
                    color = MainText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Selecciona un APK de tus archivos para consultar sus datos antes de instalarlo.",
                    color = Muted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(17.dp))

                Button(
                    onClick = onSelectApk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir archivo APK", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Apps, null, tint = Blue, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Paquetes instalados", color = MainText, fontWeight = FontWeight.SemiBold)
                    Text("$installedApps aplicaciones detectadas", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null, tint = PurpleSoft)
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        if (selectedApk == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Surface1)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        null,
                        tint = Muted,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Text("Ningún APK seleccionado", color = MainText, fontWeight = FontWeight.SemiBold)

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Los detalles del paquete aparecerán aquí.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            SelectedApkCard(
                apk = selectedApk,
                onInstall = onInstall,
                onRemove = onRemoveApk
            )
        }

        Spacer(Modifier.height(15.dp))

        InfoNote(
            "La instalación se realiza mediante Android. " +
                "GestorApps no instala paquetes en segundo plano ni evita las confirmaciones del sistema."
        )
    }
}

@Composable
fun SelectedApkCard(
    apk: SelectedApk,
    onInstall: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (apk.icon != null) {
                    DrawableIcon(apk.icon, Modifier.size(48.dp))
                } else {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, null, tint = PurpleSoft)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        apk.name,
                        color = MainText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("APK seleccionado", color = Green, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            PackageDetailLine("Paquete", apk.packageName)
            PackageDetailLine("Versión", apk.version)
            PackageDetailLine("Tamaño", formatBytes(apk.size))

            Spacer(Modifier.height(15.dp))

            Button(
                onClick = onInstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Icon(Icons.Default.InstallMobile, null, tint = Bg)
                Spacer(Modifier.width(8.dp))
                Text("Instalar", color = Bg, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Quitar APK", color = Muted)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DETALLES DE APP
// ─────────────────────────────────────────────────────────────

@Composable
fun ApplicationDetailsDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAndroidSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(app.icon, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        app.name,
                        color = MainText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        if (app.isSystem) "Aplicación del sistema" else "Aplicación de usuario",
                        color = PurpleSoft,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column {
                PackageDetailLine("Paquete", app.packageName)
                PackageDetailLine("Versión", app.version)

                Spacer(Modifier.height(10.dp))

                Text(
                    "Android controla el tamaño detallado y las acciones avanzadas desde la información de la aplicación.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text("Abrir", color = PurpleSoft)
            }
        },
        dismissButton = {
            TextButton(onClick = onAndroidSettings) {
                Text("Ajustes de Android", color = PurpleSoft)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// COMPONENTES REUTILIZABLES
// ─────────────────────────────────────────────────────────────

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 23.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow,
                color = PurpleSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                title,
                color = MainText,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, "Actualizar", tint = PurpleSoft)
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun InfoNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Surface2)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, null, tint = PurpleSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun PackageDetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.width(78.dp)
        )

        Text(
            value,
            color = MainText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DrawableIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        drawable.toBitmap(width = 96, height = 96).asImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(13.dp))
    )
}

// ─────────────────────────────────────────────────────────────
// UTILIDADES
// ─────────────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0

    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }

    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
