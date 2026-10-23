package com.cunicella.fichaje.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.update.Updates
import com.cunicella.fichaje.widget.ClockWidget
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { FichajeTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val status by Supa.client.auth.sessionStatus.collectAsStateWithLifecycle()
    var update by remember { mutableStateOf<Updates.Available?>(null) }
    LaunchedEffect(Unit) { update = Updates.check() }
    when (status) {
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> Main(onUpdateFound = { update = it })
        is SessionStatus.NotAuthenticated -> LoginScreen()
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    update?.let { UpdateDialog(it, onDismiss = { update = null }) }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Hoy("Hoy", Icons.Filled.Home),
    Equipo("Equipo", Icons.Filled.Person),
    Historial("Historial", Icons.Filled.DateRange),
    Cobrar("Cobrar", Icons.Filled.ShoppingCart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(onUpdateFound: (Updates.Available) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Hoy) }
    val hoy: HoyViewModel = viewModel()
    val data: DataViewModel = viewModel()
    val ui by hoy.ui.collectAsStateWithLifecycle()
    val dataUi by data.ui.collectAsStateWithLifecycle()
    // al volver a la app (p. ej. después de fichar desde el widget) se recarga
    LifecycleResumeEffect(Unit) {
        hoy.refresh()
        data.refresh(quiet = true)
        onPauseOrDispose { }
    }
    var configOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    val isAdmin = ui.snap?.me?.isAdmin == true
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // permiso para la notificación de jornada en curso (Android 13+)
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val snap = ui.snap
        if (granted && snap != null) scope.launch { ClockWidget.show(context, snap) }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    val signOut: () -> Unit = {
        accountOpen = false
        scope.launch {
            Supa.client.auth.signOut()
            ClockWidget.refreshAll(context)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.snap?.settings?.company ?: "Fichaje", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    if (isAdmin) IconButton(onClick = { configOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = "Configuración") }
                    IconButton(onClick = { accountOpen = true }) { Avatar(ui.snap?.me?.initials ?: "?") }
                    IconButton(onClick = signOut) { Icon(Icons.Filled.ExitToApp, contentDescription = "Cerrar sesión") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Hoy -> HoyScreen(hoy)
                Tab.Equipo -> EquipoScreen(data)
                Tab.Historial -> HistorialScreen(data)
                Tab.Cobrar -> CobrarScreen(data)
            }
        }
    }
    if (accountOpen) {
        AccountSheet(
            me = ui.snap?.me,
            email = Supa.client.auth.currentUserOrNull()?.email,
            settings = ui.snap?.settings,
            onUpdateFound = { accountOpen = false; onUpdateFound(it) },
            onSignOut = signOut,
            onDismiss = { accountOpen = false },
            onEditProfile = if (dataUi.store?.me != null) ({ accountOpen = false; profileOpen = true }) else null,
            onSettings = if (isAdmin) ({ accountOpen = false; configOpen = true }) else null,
        )
    }
    val store = dataUi.store
    if (configOpen && store != null) ConfigSheet(store.settings, data, onDismiss = { configOpen = false })
    if (profileOpen && store != null) MemberSheet(store.me, store, data, onDismiss = { profileOpen = false })
}
