package com.vorcanor.gestorapps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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

private val Background = Color(0xFF101114)
private val CardColor = Color(0xFF1B1B20)
private val Accent = Color(0xFFA78BFA)
private val MainText = Color(0xFFF1F0F5)
private val SecondaryText = Color(0xFFAAAAB2)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val version: String
)

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

    fun loadApps() {
        scope.launch {
            loading = true

            try {
                val result = withContext(Dispatchers.IO) {
                    getInstalledApps(context.packageManager)
                }

                apps = result
            } catch (e: Exception) {
                apps = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
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
                                text = "GestorApps",
                                color = MainText,
                                fontSize = 23.sp
                            )

                            Text(
                                text = "Administrador de aplicaciones",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    },

                    actions = {
                        IconButton(
                            onClick = { loadApps() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
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

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),

                    placeholder = {
                        Text("Buscar aplicaciones...")
                    },

                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    },

                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),

                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = MainText,
                        unfocusedTextColor = MainText
                    )
                )

                Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "${visibleApps.size} aplicaciones",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )

                    TextButton(
                        onClick = { ascending = !ascending }
                    ) {
                        Text(
                            text = if (ascending) "A-Z" else "Z-A",
                            color = Accent
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {

                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Accent
                            )
                        }
                    }

                    visibleApps.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No se encontraron aplicaciones",
                                color = SecondaryText
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {

                            items(
                                items = visibleApps,
                                key = { it.packageName }
                            ) { app ->

                                AppCard(
                                    app = app,
                                    onClick = {
                                        selectedApp = app
                                    }
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

                onDismiss = {
                    selectedApp = null
                },

                onOpen = {
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(app.packageName)

                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },

                onSettings = {
                    val settingsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${app.packageName}")
                    )

                    context.startActivity(settingsIntent)
                },

                onUninstall = {
                    val uninstallIntent = Intent(
                        Intent.ACTION_DELETE,
                        Uri.parse("package:${app.packageName}")
                    )

                    context.startActivity(uninstallIntent)
                }
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

                modifier = Modifier.size(45.dp)
            )

            Spacer(Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = app.name,
                    color = MainText,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = app.packageName,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            Text(
                text = if (app.isSystem) "Sistema" else "Usuario",
                color = Accent,
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
            Text(
                text = app.name,
                color = MainText
            )
        },

        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Text(
                    text = "Paquete: ${app.packageName}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )

                Text(
                    text = "Versión: ${app.version}",
                    color = SecondaryText
                )

                Text(
                    text = if (app.isSystem) {
                        "Aplicación del sistema"
                    } else {
                        "Aplicación de usuario"
                    },
                    color = Accent
                )
            }
        },

        confirmButton = {
            TextButton(onClick = onOpen) {
                Text("Abrir", color = Accent)
            }
        },

        dismissButton = {
            Row {

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
                0
            )

            val version = packageInfo.versionName ?: "Desconocida"

            val isSystem =
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            InstalledApp(
                name = name,
                packageName = info.packageName,
                icon = packageManager.getApplicationIcon(info),
                isSystem = isSystem,
                version = version
            )

        } catch (e: Exception) {
            null
        }

    }.sortedBy { it.name.lowercase() }
}
