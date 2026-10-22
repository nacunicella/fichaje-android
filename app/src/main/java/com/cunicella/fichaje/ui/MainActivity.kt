package com.cunicella.fichaje.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.widget.ClockWidget
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FichajeTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val status by Supa.client.auth.sessionStatus.collectAsStateWithLifecycle()
    when (status) {
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> Main()
        is SessionStatus.NotAuthenticated -> LoginScreen()
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Hoy("Hoy", Icons.Filled.Home),
    Equipo("Equipo", Icons.Filled.Person),
    Historial("Historial", Icons.Filled.DateRange),
    Cobrar("Cobrar", Icons.Filled.ShoppingCart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main() {
    var tab by rememberSaveable { mutableStateOf(Tab.Hoy) }
    val hoy: HoyViewModel = viewModel()
    val ui by hoy.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.snap?.settings?.company ?: "Fichaje", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            Supa.client.auth.signOut()
                            ClockWidget.refreshAll(context)
                        }
                    }) { Icon(Icons.Filled.ExitToApp, contentDescription = "Cerrar sesión") }
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
                else -> ComingSoon(tab.label)
            }
        }
    }
}

@Composable
private fun ComingSoon(name: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Llega en la próxima versión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
