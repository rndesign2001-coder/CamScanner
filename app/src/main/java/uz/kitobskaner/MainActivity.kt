package uz.kitobskaner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import uz.kitobskaner.data.ScanMode
import uz.kitobskaner.ui.export.ExportScreen
import uz.kitobskaner.ui.main.MainScreen
import uz.kitobskaner.ui.page.PageScreen
import uz.kitobskaner.ui.project.ProjectScreen
import uz.kitobskaner.ui.result.ResultScreen
import uz.kitobskaner.ui.settings.SettingsScreen
import uz.kitobskaner.ui.theme.KitobTheme
import uz.kitobskaner.ui.tools.CompressScreen
import uz.kitobskaner.ui.tools.ConvertScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    fun project(id: String) = "project/$id"
    fun page(id: String, index: Int) = "page/$id/$index"
    fun export(id: String) = "export/$id"
    fun result(path: String) = "result?path=" + URLEncoder.encode(path, "UTF-8")
    fun compress(path: String? = null) = "compress" + (path?.let { "?path=" + URLEncoder.encode(it, "UTF-8") } ?: "")
    fun convert(target: String? = null) = "convert" + (target?.let { "?target=$it" } ?: "")
}

class MainActivity : ComponentActivity() {

    private val openProject = MutableStateFlow<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as App
        lifecycleScope.launch { app.repository.ensureLoaded() }
        app.exporter.refreshFiles()
        if (savedInstanceState == null) handleShare(intent)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val settings by app.settings.collectAsState()
            KitobTheme(settings.theme, settings.palette) {
                val nav = rememberNavController()
                val pending by openProject.collectAsState()
                LaunchedEffect(pending) {
                    pending?.let {
                        nav.navigate(Routes.project(it))
                        openProject.value = null
                    }
                }
                NavHost(
                    navController = nav,
                    startDestination = "main",
                    enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
                ) {
                    composable("main") {
                        MainScreen(
                            onOpenProject = { nav.navigate(Routes.project(it)) },
                            onOpenFile = { nav.navigate(Routes.result(it.absolutePath)) },
                            onSettings = { nav.navigate("settings") },
                            onCompress = { nav.navigate(Routes.compress()) },
                            onConvert = { nav.navigate(Routes.convert(it)) },
                        )
                    }
                    composable("project/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id") ?: return@composable
                        ProjectScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() },
                            onOpenPage = { index -> nav.navigate(Routes.page(id, index)) },
                            onExport = { nav.navigate(Routes.export(id)) },
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
                        ExportScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() },
                            onDone = { file -> nav.navigate(Routes.result(file.absolutePath)) },
                        )
                    }
                    composable(
                        "result?path={path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                    ) {
                        val path = URLDecoder.decode(it.arguments?.getString("path") ?: "", "UTF-8")
                        ResultScreen(
                            path = path,
                            onClose = { nav.popBackStack() },
                            onHome = { nav.popBackStack("main", inclusive = false) },
                            onCompress = { p -> nav.navigate(Routes.compress(p)) },
                        )
                    }
                    composable(
                        "compress?path={path}",
                        arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                    ) {
                        val path = URLDecoder.decode(it.arguments?.getString("path") ?: "", "UTF-8")
                        CompressScreen(
                            initialPath = path.ifEmpty { null },
                            onBack = { nav.popBackStack() },
                            onDone = { f -> nav.navigate(Routes.result(f.absolutePath)) { popUpTo("main") } },
                        )
                    }
                    composable(
                        "convert?target={target}",
                        arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "" })
                    ) {
                        ConvertScreen(
                            initialTarget = it.arguments?.getString("target")?.ifEmpty { null },
                            onBack = { nav.popBackStack() },
                            onOpenFile = { f -> nav.navigate(Routes.result(f.absolutePath)) },
                            onOpenProject = { id -> nav.navigate(Routes.project(id)) { popUpTo("main") } },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() }, onLanguageChanged = { recreate() })
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
            val isPdf = intent.type == "application/pdf"
            val p = if (isPdf) {
                app.repository.createFromPdf(uris.first(), uz.kitobskaner.pdf.PdfTools.displayName(this@MainActivity, uris.first()))
            } else {
                app.repository.createProject(
                    uris,
                    splitSpreads = app.settings.value.scanMode == ScanMode.SPREAD,
                    titlePrefix = getString(R.string.default_book_title),
                )
            }
            if (p != null) openProject.value = p.id
        }
    }
}
