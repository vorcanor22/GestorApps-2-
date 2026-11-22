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
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Background = Color(0xFF101114)
private val CardColor = Color(0xFF1B1B20)
private val Accent = Color(0xFFA78BFA)
private val MainText = Color(0xFFF1F0F5)
private val SecondaryText = Color(0xFFAAAAB2)
private val Green = Color(0xFF80D6A1)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val version: String,
    val installDate: Long,
    val updateDate: Long,
    val targetSdk: Int,
    val permissions: List<String>
)

data class StorageInfo(
    val total: Long,
    val available: Long
) {
    val used: Long
        get() = total - available

    val percentage: Float
        get() = if (total > 0) used.toFloat() / total else 0f
}

enum class AppFilter {
    ALL, USER, SYSTEM
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GestorAppsApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestorAppsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember {
        mutableStateOf<List<InstalledApp>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var search by remember {
        mutableStateOf("")
    }

    var filter by remember {
        mutableStateOf(AppFilter.ALL)
    }

    var ascending by remember {
        mutableStateOf(true)
    }

    var selectedApp by remember {
        mutableStateOf<InstalledApp?>(null)
    }

    var storage by remember {
        mutableStateOf<StorageInfo?>(null)
    }

    fun loadData() {
        scope.launch {
            loading = true
            errorMessage = null

            try {
                val result = withContext(Dispatchers.IO) {
                    getInstalledApps(context.packageManager)
                }

                apps = result

                storage = withContext(Dispatchers.IO) {
                    getStorageInfo()
                }
            } catch (e: Exception) {
                errorMessage = "No se pudo cargar la información."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val visibleApps = remember(apps, search, filter, ascending) {
        apps.filter { app ->
            val matchesSearch =
                app.name.contains(search, ignoreCase = true) ||
                app.packageName.contains(search, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
            }

            matchesSearch && matchesFilter
        }.let { result ->
            if (ascending) {
                result.sortedBy { it.name.lowercase() }
            } else {
                result.sortedByDescending { it.name.lowercase() }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = CardColor,
            onBackground = MainText,
            onSurface = MainText
        )
    ) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "GestorApps",
                                color = MainText,
                                fontSize = 24.sp
                            )
                            Text(
                                "Tu centro de aplicaciones",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadData() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Actualizar",
                                tint = Accent
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Background
                    )
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {

                if (storage != null) {
                    StorageCard(storage!!)
                    Spacer(Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Buscar aplicaciones...")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = MainText,
                        unfocusedTextColor = MainText,
                        cursorColor = Accent
                    )
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == AppFilter.ALL,
                        onClick = { filter = AppFilter.ALL },
                        label = { Text("Todas") }
                    )

                    FilterChip(
                        selected = filter == AppFilter.USER,
                        onClick = { filter = AppFilter.USER },
                        label = { Text("Usuario") }
                    )

                    FilterChip(
                        selected = filter == AppFilter.SYSTEM,
                        onClick = { filter = AppFilter.SYSTEM },
                        label = { Text("Sistema") }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${visibleApps.size} aplicaciones",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )

                    TextButton(
                        onClick = { ascending = !ascending }
                    ) {
                        Text(
                            if (ascending) "A-Z" else "Z-A",
                            color = Accent
                        )
                    }
                }

                when {
                    loading -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Accent)
                        }
                    }

                    errorMessage != null -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    errorMessage!!,
                                    color = SecondaryText
                                )
                                TextButton(onClick = { loadData() }) {
                                    Text("Reintentar", color = Accent)
                                }
                            }
                        }
                    }

                    visibleApps.isEmpty() -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No se encontraron aplicaciones",
                                color = SecondaryText
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(
                                items = visibleApps,
                                key = { it.packageName }
                            ) { app ->
                                AppCard(
                                    app = app,
                                    onClick = { selectedApp = app }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedApp?.let { app ->
            AppDetailsDialog(
                app = app,
                onDismiss = { selectedApp = null },
                onOpen = {
                    selectedApp = null

                    val intent = context.packageManager
                        .getLaunchIntentForPackage(app.packageName)

                    if (intent != null) {
                        context.startActivity(intent)
                    }
                },
                onSettings = {
                    selectedApp = null

                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${app.packageName}")
                    )

                    context.startActivity(intent)
                },
                onUninstall = {
                    selectedApp = null

                    val intent = Intent(
                        Intent.ACTION_DELETE,
                        Uri.parse("package:${app.packageName}")
                    )

                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun StorageCard(storage: StorageInfo) {
    val percentage = (storage.percentage * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = Accent
                )

                Spacer(Modifier.width(9.dp))

                Text(
                    "Almacenamiento del dispositivo",
                    color = MainText,
                    fontSize = 14.sp
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "$percentage%",
                    color = Accent,
                    fontSize = 15.sp
                )
            }

            LinearProgressIndicator(
                progress = { storage.percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Accent,
                trackColor = Color(0xFF34343C)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Utilizado", color = SecondaryText, fontSize = 11.sp)
                    Text(
                        formatBytes(storage.used),
                        color = MainText,
                        fontSize = 13.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Disponible", color = SecondaryText, fontSize = 11.sp)
                    Text(
                        formatBytes(storage.available),
                        color = Green,
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                "Capacidad total: ${formatBytes(storage.total)}",
                color = SecondaryText,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AppCard(
    app: InstalledApp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { imageContext ->
                    ImageView(imageContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = {
                    it.setImageDrawable(app.icon)
                },
                modifier = Modifier.size(46.dp)
            )

            Spacer(Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    app.name,
                    color = MainText,
                    fontSize = 15.sp,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    app.packageName,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Versión ${app.version}",
                    color = Accent,
                    fontSize = 11.sp
                )
            }

            Text(
                if (app.isSystem) "Sistema" else "Usuario",
                color = if (app.isSystem) SecondaryText else Green,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun AppDetailsDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = {
            Text(app.name, color = MainText)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DetailLine("Paquete", app.packageName)
                DetailLine("Versión", app.version)
                DetailLine(
                    "Tipo",
                    if (app.isSystem) "Aplicación del sistema"
                    else "Aplicación de usuario"
                )
                DetailLine("SDK objetivo", app.targetSdk.toString())
                DetailLine("Instalada", formatDate(app.installDate))
                DetailLine("Actualizada", formatDate(app.updateDate))

                HorizontalDivider(color = Color.DarkGray)

                Text(
                    "Permisos declarados (${app.permissions.size})",
                    color = Accent,
                    fontSize = 13.sp
                )

                if (app.permissions.isEmpty()) {
                    Text(
                        "No declara permisos.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                } else {
                    app.permissions.take(12).forEach { permission ->
                        Text(
                            "• ${permission.substringAfterLast('.')}",
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }

                    if (app.permissions.size > 12) {
                        Text(
                            "Y ${app.permissions.size - 12} permisos más...",
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    "La lista muestra permisos declarados por la aplicación, " +
                        "no confirma cuáles están concedidos.",
                    color = SecondaryText,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text("Abrir", color = Accent)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSettings) {
                    Text("Ajustes", color = Accent)
                }

                if (!app.isSystem) {
                    TextButton(onClick = onUninstall) {
                        Text(
                            "Desinstalar",
                            color = Color(0xFFFF7777)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label,
            color = SecondaryText,
            fontSize = 11.sp
        )
        Text(
            value,
            color = MainText,
            fontSize = 12.sp
        )
    }
}

private fun getInstalledApps(
    packageManager: PackageManager
): List<InstalledApp> {
    val installed = packageManager.getInstalledApplications(0)

    return installed.mapNotNull { info ->
        try {
            val name = packageManager
                .getApplicationLabel(info)
                .toString()

            val packageInfo = packageManager.getPackageInfo(
                info.packageName,
                PackageManager.GET_PERMISSIONS
            )

            val version = packageInfo.versionName ?: "Desconocida"

            val isSystem =
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            InstalledApp(
                name = name,
                packageName = info.packageName,
                icon = packageManager.getApplicationIcon(info),
                isSystem = isSystem,
                version = version,
                installDate = packageInfo.firstInstallTime,
                updateDate = packageInfo.lastUpdateTime,
                targetSdk = info.targetSdkVersion,
                permissions = packageInfo.requestedPermissions?.toList()
                    ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.name.lowercase() }
}

private fun getStorageInfo(): StorageInfo {
    val stat = StatFs(Environment.getDataDirectory().path)

    val total = stat.totalBytes
    val available = stat.availableBytes

    return StorageInfo(
        total = total,
        available = available
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"

    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format(
            Locale.getDefault(),
            "%.1f GB",
            bytes / gb
        )

        bytes >= mb -> String.format(
            Locale.getDefault(),
            "%.0f MB",
            bytes / mb
        )

        else -> String.format(
            Locale.getDefault(),
            "%.0f KB",
            bytes / kb
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Desconocida"

    return SimpleDateFormat(
        "dd/MM/yyyy",
        Locale.getDefault()
    ).format(Date(timestamp))
}
