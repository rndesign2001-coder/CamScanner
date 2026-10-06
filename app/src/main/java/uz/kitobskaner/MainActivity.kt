package uz.kitobskaner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.kitobskaner.ui.export.ExportScreen
import uz.kitobskaner.ui.home.HomeScreen
import uz.kitobskaner.ui.page.PageScreen
import uz.kitobskaner.ui.project.ProjectScreen
import uz.kitobskaner.ui.settings.SettingsScreen
import uz.kitobskaner.ui.theme.KitobTheme

class MainActivity : ComponentActivity() {

    /** Boshqa ilovadan "Ulashish" orqali kelgan rasmlardan yaratilgan loyiha. */
    private val openProject = MutableStateFlow<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as App
        lifecycleScope.launch { app.repository.ensureLoaded() }
        if (savedInstanceState == null) handleShare(intent)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val settings by app.settings.collectAsState()
            KitobTheme(settings.theme) {
                val nav = rememberNavController()
                val pending by openProject.collectAsState()
                LaunchedEffect(pending) {
                    pending?.let {
                        nav.navigate("project/$it")
                        openProject.value = null
                    }
                }
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpen = { nav.navigate("project/$it") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("project/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        ProjectScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() },
                            onOpenPage = { index -> nav.navigate("page/$id/$index") },
                            onExport = { nav.navigate("export/$id") },
                        )
                    }
                    composable(
                        "page/{id}/{index}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("index") { type = NavType.IntType },
                        )
                    ) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        val index = it.arguments?.getInt("index") ?: 0
                        PageScreen(projectId = id, startIndex = index, onBack = { nav.popBackStack() })
                    }
                    composable("export/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        ExportScreen(projectId = id, onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val app = application as App
        lifecycleScope.launch {
            val p = app.repository.createProject(uris)
            if (p != null) openProject.value = p.id
        }
    }
}
