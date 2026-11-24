package com.vorcanor.gestorapps

import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B0C11)
private val Surface1 = Color(0xFF151720)
private val Surface2 = Color(0xFF20222D)
private val Purple = Color(0xFF9B7BFF)
private val PurpleSoft = Color(0xFFC4B3FF)
private val Green = Color(0xFF5CDBA0)
private val Blue = Color(0xFF6CB8FF)
private val Orange = Color(0xFFFFB86C)
private val MainText = Color(0xFFF4F3FA)
private val Muted = Color(0xFF9396A8)

private data class InstalledApp(
    val name: String,
    val packageName: String,
    val version: String,
    val isSystem: Boolean,
    val icon: Drawable,
    val apkBytes: Long? = null,
    val dataBytes: Long? = null,
    val cacheBytes: Long? = null
) {
    val totalBytes: Long?
        get() = if (apkBytes != null && dataBytes != null && cacheBytes != null)
            apkBytes + dataBytes + cacheBytes else null
}

private data class DeviceStorage(
    val total: Long,
    val used: Long,
    val free: Long,
    val apps: Long = 0,
    val appData: Long = 0,
    val cache: Long = 0,
    val other: Long = 0
)

private data class BatteryInfo(
    val level: Int,
    val scale: Int,
    val status: Int,
    val health: Int,
    val temperature: Float,
    val voltage: Float,
    val hoursLeft: Double?
)

private data class SelectedApk(
    val uri: Uri,
    val name: String,
    val packageName: String,
    val version: String,
    val size: Long,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GestorAppsRoot()
        }
    }
}

@Composable
fun GestorAppsRoot() {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var storage by remember { mutableStateOf<DeviceStorage?>(null) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedApk by remember { mutableStateOf<SelectedApk?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todas") }
    var sortAZ by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf("") }
    var usageAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var battery by remember { mutableStateOf(readBatteryInfo(context)) }

    fun refresh() {
        if (loading) return
        loading = true

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val access = hasUsageAccess(context)

                val apps = try {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .mapNotNull { info ->
                            try {
                                val pi = pm.getPackageInfo(info.packageName, 0)

                                val stats = if (access) {
                                    queryPackageStorage(context, info.packageName)
                                } else null

                                InstalledApp(
                                    pm.getApplicationLabel(info).toString(),
                                    info.packageName,
                                    pi.versionName ?: "—",
                                    (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                                    pm.getApplicationIcon(info),
                                    stats?.first,
                                    stats?.second,
                                    stats?.third
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }
                        .sortedBy { it.name.lowercase(Locale.getDefault()) }
                } catch (_: Exception) {
                    emptyList()
                }

                val stat = try {
                    val fs = StatFs(Environment.getDataDirectory().absolutePath)
                    val total = fs.totalBytes
                    val free = fs.availableBytes

                    val appBytes = apps.sumOf { it.apkBytes ?: 0L }
                    val dataBytes = apps.sumOf { it.dataBytes ?: 0L }
                    val cacheBytes = apps.sumOf { it.cacheBytes ?: 0L }

                    DeviceStorage(
                        total,
                        (total - free).coerceAtLeast(0),
                        free,
                        appBytes,
                        dataBytes,
                        cacheBytes,
                        (total - free - appBytes - dataBytes - cacheBytes)
                            .coerceAtLeast(0)
                    )
                } catch (_: Exception) {
                    null
                }

                Triple(apps, stat, access)
            }

            installed = result.first
            storage = result.second
            usageAccess = result.third
            battery = readBatteryInfo(context)
            updatedAt = SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())

            loading = false
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                battery = readBatteryInfo(context, intent)
            }
        }

        val filterIntent = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        ContextCompat.registerReceiver(
            context,
            receiver,
            filterIntent,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val temp = File(context.cacheDir, "gestorapps_chosen.apk")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val archive = pm.getPackageArchiveInfo(
                    temp.absolutePath,
                    PackageManager.GET_ACTIVITIES
                )

                if (archive != null) {
                    val ai = archive.applicationInfo
                    ai.sourceDir = temp.absolutePath
                    ai.publicSourceDir = temp.absolutePath

                    val label = try {
                        pm.getApplicationLabel(ai).toString()
                    } catch (_: Exception) {
                        temp.name
                    }

                    val icon = try {
                        pm.getApplicationIcon(ai)
                    } catch (_: Exception) {
                        null
                    }

                    selectedApk = SelectedApk(
                        uri,
                        label,
                        archive.packageName ?: "Desconocido",
                        archive.versionName ?: "—",
                        temp.length(),
                        icon
                    )
                } else {
                    selectedApk = SelectedApk(
                        uri,
                        temp.name,
                        "APK no reconocido",
                        "—",
                        temp.length(),
                        null
                    )
                }
            } catch (_: Exception) {
                selectedApk = null
            }
        }
    }

    val visibleApps = installed.filter { app ->
        val matches = app.name.contains(search, true) ||
            app.packageName.contains(search, true)

        val filterMatches = when (filter) {
            "Usuario" -> !app.isSystem
            "Sistema" -> app.isSystem
            else -> true
        }

        matches && filterMatches
    }.let {
        if (sortAZ) {
            it.sortedBy { app -> app.name.lowercase(Locale.getDefault()) }
        } else {
            it.sortedByDescending { app -> app.name.lowercase(Locale.getDefault()) }
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
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        tab == 0,
                        { tab = 0 },
                        icon = { Icon(Icons.Default.DonutLarge, null) },
                        label = { Text("Espacio") },
                        colors = navColors()
                    )

                    NavigationBarItem(
                        tab == 1,
                        { tab = 1 },
                        icon = { Icon(Icons.Default.Apps, null) },
                        label = { Text("Aplicaciones") },
                        colors = navColors()
                    )

                    NavigationBarItem(
                        tab == 2,
                        { tab = 2 },
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text("Paquetes") },
                        colors = navColors()
                    )

                    NavigationBarItem(
                        tab == 3,
                        { tab = 3 },
                        icon = { Icon(Icons.Default.BatteryFull, null) },
                        label = { Text("Batería") },
                        colors = navColors()
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> StorageScreen(
                        storage,
                        installed.size,
                        loading,
                        updatedAt,
                        ::refresh,
                        usageAccess,
                        {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )

                    1 -> AppsScreen(
                        visibleApps,
                        installed.size,
                        search,
                        { search = it },
                        filter,
                        { filter = it },
                        sortAZ,
                        { sortAZ = !sortAZ },
                        ::refresh,
                        { selectedApp = it }
                    )

                    2 -> PackagesScreen(
                        selectedApk,
                        {
                            apkPicker.launch(
                                arrayOf("application/vnd.android.package-archive")
                            )
                        },
                        { selectedApk = null },
                        {
                            selectedApk?.let { item ->
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                item.uri,
                                                "application/vnd.android.package-archive"
                                            )
                                            addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        }
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        },
                        installed.size,
                        ::refresh
                    )

                    else -> BatteryScreen(battery, ::refresh)
                }
            }
        }

        selectedApp?.let { app ->
            AppDetailsDialog(
                app,
                { selectedApp = null },
                {
                    try {
                        pm.getLaunchIntentForPackage(app.packageName)
                            ?.let(context::startActivity)
                    } catch (_: Exception) {
                    }
                },
                {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun hasUsageAccess(context: Context): Boolean {
    return try {
        val ops = context.getSystemService(
            Context.APP_OPS_SERVICE
        ) as AppOpsManager

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

private fun queryPackageStorage(
    context: Context,
    packageName: String
): Triple<Long, Long, Long>? {
    return try {
        val manager = context.getSystemService(
            Context.STORAGE_STATS_SERVICE
        ) as StorageStatsManager

        val stats = manager.queryStatsForPackage(
            StorageManager.UUID_DEFAULT,
            packageName,
            Process.myUserHandle()
        )

        Triple(
            stats.appBytes,
            stats.dataBytes,
            stats.cacheBytes
        )
    } catch (_: Exception) {
        null
    }
}

private fun readBatteryInfo(
    context: Context,
    intent: Intent? = null
): BatteryInfo {
    val data = intent ?: context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    val level = data?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = data?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100

    val manager = context.getSystemService(
        Context.BATTERY_SERVICE
    ) as BatteryManager

    val charge = manager.getLongProperty(
        BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
    )

    val current = manager.getLongProperty(
        BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE
    )

    val hours = if (
        charge > 0 &&
        current != Long.MIN_VALUE &&
        abs(current) > 0
    ) {
        charge.toDouble() / abs(current).toDouble()
    } else {
        null
    }

    return BatteryInfo(
        level,
        scale,
        data?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
        data?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1,
        (data?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10f,
        (data?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1) / 1000f,
        hours
    )
}

@Composable
private fun navColors() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = PurpleSoft,
        selectedTextColor = PurpleSoft,
        indicatorColor = Color(0xFF30264A),
        unselectedIconColor = Muted,
        unselectedTextColor = Muted
    )

// STORAGE SCREEN

@Composable
fun StorageScreen(
    storage: DeviceStorage?,
    appCount: Int,
    loading: Boolean,
    updatedAt: String,
    onRefresh: () -> Unit,
    usageAccess: Boolean,
    onGrantAccess: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader("VORCANOR", "Almacenamiento", onRefresh)

        if (storage == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Purple)
                } else {
                    Text(
                        "No se pudo leer el almacenamiento",
                        color = Muted
                    )
                }
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StorageRingCard(storage)
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StorageStat(
                        "Ocupado",
                        formatBytes(storage.used),
                        Purple,
                        Modifier.weight(1f)
                    )

                    StorageStat(
                        "Disponible",
                        formatBytes(storage.free),
                        Green,
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionTitle(
                    "Uso del dispositivo",
                    "Resumen general"
                )
            }

            item {
                SectionTitle(
                    "Desglose de aplicaciones",
                    if (usageAccess) {
                        "Datos proporcionados por Android"
                    } else {
                        "Necesita permiso de acceso al uso"
                    }
                )
            }

            if (!usageAccess) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Surface1
                        )
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text(
                                "Activa el acceso a los datos de uso",
                                color = MainText,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                "Android necesita que autorices a GestorApps para consultar el tamaño de cada aplicación.",
                                color = Muted,
                                fontSize = 12.sp
                            )

                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = onGrantAccess,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Purple
                                )
                            ) {
                                Text("Conceder acceso")
                            }
                        }
                    }
                }
            } else {
                item {
                    StorageCategory(
                        "Aplicaciones (APK)",
                        "$appCount aplicaciones",
                        formatBytes(storage.apps),
                        Icons.Default.Apps,
                        Purple
                    )
                }

                item {
                    StorageCategory(
                        "Datos de aplicaciones",
                        "Datos internos y archivos de apps",
                        formatBytes(storage.appData),
                        Icons.Default.Folder,
                        Blue
                    )
                }

                item {
                    StorageCategory(
                        "Caché de aplicaciones",
                        "Archivos temporales de apps",
                        formatBytes(storage.cache),
                        Icons.Default.Cached,
                        Orange
                    )
                }

                item {
                    StorageCategory(
                        "Sistema y otros",
                        "Espacio restante no atribuido a apps",
                        formatBytes(storage.other),
                        Icons.Default.Storage,
                        Green
                    )
                }

                item {
                    InfoNote(
                        "Las cifras por aplicación proceden de StorageStatsManager. Android puede limitar u ocultar datos de algunos paquetes; por eso el total de categorías puede no coincidir exactamente con el almacenamiento ocupado."
                    )
                }
            }

            if (updatedAt.isNotBlank()) {
                item {
                    Text(
                        "Actualizado: $updatedAt",
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
    val fraction = if (storage.total > 0) {
        (storage.used.toFloat() / storage.total).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animated by animateFloatAsState(
        fraction,
        label = "storageRing"
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(226.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        Surface2,
                        -90f,
                        360f,
                        false,
                        style = Stroke(21.dp.toPx())
                    )

                    drawArc(
                        Purple,
                        -90f,
                        360f * animated,
                        false,
                        style = Stroke(21.dp.toPx())
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(fraction * 100).toInt()}%",
                        color = MainText,
                        fontSize = 43.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text("ocupado", color = Muted, fontSize = 14.sp)

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "${formatBytes(storage.used)} / ${formatBytes(storage.total)}",
                        color = PurpleSoft,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

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
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(43.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = MainText,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            value?.let {
                Text(
                    it,
                    color = MainText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// APPLICATIONS SCREEN

@Composable
fun AppsScreen(
    apps: List<InstalledApp>,
    total: Int,
    search: String,
    onSearch: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    sortAZ: Boolean,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (InstalledApp) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 18.dp)
    ) {
        ScreenHeader("VORCANOR", "Aplicaciones", onRefresh)

        Text(
            "$total aplicaciones instaladas",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(13.dp))

        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Buscar aplicaciones", color = Muted)
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = PurpleSoft
                )
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(
                            Icons.Default.Close,
                            "Limpiar",
                            tint = Muted
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = Surface2,
                focusedTextColor = MainText,
                unfocusedTextColor = MainText,
                cursorColor = Purple
            )
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Todas", "Usuario", "Sistema").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilter(option) },
                        label = { Text(option, fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp),
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
                    "Ordenar",
                    tint = PurpleSoft
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        if (apps.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        tint = Muted,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("No hay resultados", color = Muted)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(app) {
                        onAppClick(app)
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableIcon(app.icon, Modifier.size(47.dp))

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
                    if (app.isSystem) {
                        "Aplicación del sistema"
                    } else {
                        "Aplicación de usuario"
                    },
                    color = Muted,
                    fontSize = 11.sp
                )

                Text(
                    "Versión ${app.version}",
                    color = PurpleSoft,
                    fontSize = 11.sp,
                    maxLines = 1
                )

                Text(
                    app.totalBytes?.let {
                        "Tamaño: ${formatBytes(it)}"
                    } ?: "Tamaño: no disponible",
                    color = if (app.totalBytes != null) Green else Muted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Muted
            )
        }
    }
}

// PACKAGES SCREEN

@Composable
fun PackagesScreen(
    selectedApk: SelectedApk?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onInstall: () -> Unit,
    installedApps: Int,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader("VORCANOR", "Paquetes APK", onRefresh)

        Text(
            "Selecciona y analiza un APK antes de instalarlo.",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(17.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Column(Modifier.padding(18.dp)) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        null,
                        tint = PurpleSoft,
                        modifier = Modifier.size(31.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Instalador de paquetes",
                    color = MainText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Selecciona un archivo APK de tu dispositivo.",
                    color = Muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onSelect,
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Elegir archivo APK",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(13.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(19.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1)
        ) {
            Row(
                Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Apps,
                    null,
                    tint = Blue,
                    modifier = Modifier.size(27.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Apps instaladas",
                        color = MainText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "$installedApps paquetes detectados",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        tint = PurpleSoft
                    )
                }
            }
        }

        Spacer(Modifier.height(13.dp))

        if (selectedApk == null) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface1)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        null,
                        tint = Muted,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Ningún APK seleccionado",
                        color = MainText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "Selecciona un archivo para ver sus datos.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            SelectedApkCard(selectedApk, onInstall, onRemove)
        }

        Spacer(Modifier.height(14.dp))

        InfoNote(
            "Android abrirá su instalador y pedirá confirmación. GestorApps no instala aplicaciones silenciosamente ni evita protecciones del sistema."
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (apk.icon != null) {
                    DrawableIcon(
                        apk.icon,
                        Modifier.size(47.dp)
                    )
                } else {
                    Box(
                        Modifier
                            .size(47.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            null,
                            tint = PurpleSoft
                        )
                    }
                }

                Spacer(Modifier.width(11.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        apk.name,
                        color = MainText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )

                    Text(
                        "APK seleccionado",
                        color = Green,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            PackageDetailLine("Paquete", apk.packageName)
            PackageDetailLine("Versión", apk.version)
            PackageDetailLine("Tamaño", formatBytes(apk.size))

            Spacer(Modifier.height(14.dp))

            Button(
                onInstall,
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green
                )
            ) {
                Icon(
                    Icons.Default.InstallMobile,
                    null,
                    tint = Bg
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    "Instalar APK",
                    color = Bg,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onRemove,
                Modifier.align(Alignment.End)
            ) {
                Text("Quitar archivo", color = Muted)
            }
        }
    }
}

// BATTERY SCREEN

@Composable
fun BatteryScreen(
    info: BatteryInfo,
    onRefresh: () -> Unit
) {
    val percent = if (info.level >= 0 && info.scale > 0) {
        (info.level * 100 / info.scale).coerceIn(0, 100)
    } else {
        0
    }

    val percentLabel = if (info.level >= 0 && info.scale > 0) {
        "$percent%"
    } else {
        "—"
    }

    val charging =
        info.status == BatteryManager.BATTERY_STATUS_CHARGING ||
        info.status == BatteryManager.BATTERY_STATUS_FULL

    val health = when (info.health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
        BatteryManager.BATTERY_HEALTH_DEAD -> "No funcional"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Voltaje excesivo"
        BatteryManager.BATTERY_HEALTH_COLD -> "Temperatura baja"
        else -> "No disponible"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader("VORCANOR", "Batería", onRefresh)

        LazyColumn(
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface1)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(190.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawArc(
                                    Surface2,
                                    -90f,
                                    360f,
                                    false,
                                    style = Stroke(17.dp.toPx())
                                )

                                drawArc(
                                    if (charging) Green else Purple,
                                    -90f,
                                    360f * percent / 100f,
                                    false,
                                    style = Stroke(17.dp.toPx())
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    if (charging) {
                                        Icons.Default.BatteryChargingFull
                                    } else {
                                        Icons.Default.BatteryFull
                                    },
                                    null,
                                    tint = if (charging) Green else PurpleSoft,
                                    modifier = Modifier.size(32.dp)
                                )

                                Text(
                                    percentLabel,
                                    color = MainText,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    if (charging) "Cargando" else "En uso",
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Text(
                            if (charging) {
                                "Batería conectada"
                            } else {
                                "Nivel de batería"
                            },
                            color = MainText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            item {
                SectionTitle(
                    "Tiempo estimado",
                    "Estimación basada en los sensores que expone el dispositivo"
                )
            }

            item {
                val remaining = info.hoursLeft?.let {
                    String.format(Locale.getDefault(), "%.1f h", it)
                } ?: "No disponible"

                StorageCategory(
                    "Autonomía restante",
                    if (charging) {
                        "El cálculo de descarga no se aplica mientras carga"
                    } else {
                        "Estimación aproximada, puede variar"
                    },
                    if (charging) "—" else remaining,
                    Icons.Default.Schedule,
                    Blue
                )
            }

            item {
                SectionTitle("Información básica")
            }

            item {
                StorageCategory(
                    "Estado",
                    when (info.status) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
                        BatteryManager.BATTERY_STATUS_FULL -> "Completa"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No está cargando"
                        else -> "Desconocido"
                    },
                    null,
                    Icons.Default.Bolt,
                    Green
                )
            }

            item {
                StorageCategory(
                    "Salud",
                    health,
                    null,
                    Icons.Default.FavoriteBorder,
                    PurpleSoft
                )
            }

            item {
                StorageCategory(
                    "Temperatura",
                    if (info.temperature >= 0) {
                        String.format(
                            Locale.getDefault(),
                            "%.1f °C",
                            info.temperature
                        )
                    } else {
                        "No disponible"
                    },
                    null,
                    Icons.Default.Thermostat,
                    Orange
                )
            }

            item {
                StorageCategory(
                    "Voltaje",
                    if (info.voltage > 0) {
                        String.format(
                            Locale.getDefault(),
                            "%.2f V",
                            info.voltage
                        )
                    } else {
                        "No disponible"
                    },
                    null,
                    Icons.Default.ElectricBolt,
                    Blue
                )
            }

            item {
                InfoNote(
                    "Android y el fabricante deciden qué datos de batería están disponibles. Las horas restantes solo aparecen si el teléfono proporciona lecturas de carga y consumo; no es una predicción exacta."
                )
            }
        }
    }
}

// APP DETAILS DIALOG

@Composable
fun AppDetailsDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(app.icon, Modifier.size(46.dp))

                Spacer(Modifier.width(11.dp))

                Column {
                    Text(
                        app.name,
                        color = MainText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )

                    Text(
                        if (app.isSystem) {
                            "App del sistema"
                        } else {
                            "App de usuario"
                        },
                        color = PurpleSoft,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column {
                PackageDetailLine("Paquete", app.packageName)
                PackageDetailLine("Versión", app.version)

                PackageDetailLine(
                    "APK",
                    app.apkBytes?.let(::formatBytes) ?: "No disponible"
                )

                PackageDetailLine(
                    "Datos",
                    app.dataBytes?.let(::formatBytes) ?: "No disponible"
                )

                PackageDetailLine(
                    "Caché",
                    app.cacheBytes?.let(::formatBytes) ?: "No disponible"
                )

                PackageDetailLine(
                    "Total",
                    app.totalBytes?.let(::formatBytes) ?: "No disponible"
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Los tamaños los facilita Android y pueden variar según permisos y versión del sistema.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onOpen) {
                Text("Abrir", color = PurpleSoft)
            }
        },
        dismissButton = {
            TextButton(onSettings) {
                Text("Ajustes", color = PurpleSoft)
            }
        }
    )
}

// COMMON UI COMPONENTS

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow,
                color = PurpleSoft,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Text(
                title,
                color = MainText,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        IconButton(onRefresh) {
            Icon(
                Icons.Default.Refresh,
                "Actualizar",
                tint = PurpleSoft
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(
            title,
            color = MainText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (subtitle != null) {
            Text(
                subtitle,
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun InfoNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Info,
            null,
            tint = PurpleSoft,
            modifier = Modifier.size(17.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text,
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PackageDetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp)
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
        drawable.toBitmap(
            width = 96,
            height = 96
        ).asImageBitmap()
    }

    Image(
        bitmap,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

// FORMATTING HELPERS

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0

    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }

    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        size,
        units[unit]
    )
}
