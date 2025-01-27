package io.github.ebraminio.inspectwidgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import com.google.accompanist.drawablepainter.rememberDrawablePainter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoxWithConstraints {
                        val width = this.maxWidth
                        val height = this.maxHeight
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(innerPadding),
                        ) { Content(width, height) }
                    }
                }
            }
        }
    }
}

fun tree(view: ViewGroup): Sequence<String> = sequence {
    view.children.forEach { x ->
        yield("+- " + x.javaClass.name.replace("android.widget.", ""))
        if (x is ViewGroup) yieldAll(tree(x).map { "|  $it" })
    }
}

@Composable
fun ColumnScope.Content(screenWidth: Dp, screenHeight: Dp) {
    val context = LocalContext.current
    val widgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost = AppWidgetHost(context, 1)
    var refreshToken by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val hostWidgetIds = remember(refreshToken) { appWidgetHost.appWidgetIds }
    hostWidgetIds.forEach { id ->
        var verticalExpand by remember { mutableStateOf(false) }
        var horizontalExpand by remember { mutableStateOf(false) }
        var infoMode by remember { mutableStateOf(true) }
        var widgetView by remember { mutableStateOf<AppWidgetHostView?>(null) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton({
                ++refreshToken
                appWidgetHost.deleteAppWidgetId(id)
            }) { Icon(Icons.Default.Clear, null) }
            IconButton({
                verticalExpand = !verticalExpand
            }) { Icon(Icons.Default.MoreVert, null) }
            IconButton({
                horizontalExpand = !horizontalExpand
            }, modifier = Modifier.rotate(90f)) { Icon(Icons.Default.MoreVert, null) }
            IconButton({ infoMode = !infoMode }) { Icon(Icons.Default.Info, null) }
        }
        AndroidView(
            factory = {
                val view = appWidgetHost.createView(context, id, widgetManager.getAppWidgetInfo(id))
                widgetView = view
                view
            },
            update = {
                val info = widgetManager.getAppWidgetInfo(id)
                val width = with(density) {
                    if (verticalExpand) info.minWidth.toDp() else screenWidth
                }.value
                val height = with(density) {
                    if (verticalExpand) info.minHeight.toDp() else screenWidth
                }.value * 2
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    it.updateAppWidgetSize(Bundle.EMPTY, listOf(SizeF(width, height)))
                } else it.updateAppWidgetSize(
                    null, width.toInt(), height.toInt(), width.toInt(), height.toInt()
                )
            },
            modifier = Modifier
                .height(400.dp)
                .fillMaxWidth(),
        )
        widgetView?.let { view ->
            if (infoMode) {
                Text(
                    tree(view).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily(Font(DeviceFontFamilyName("monospace")))
                    )
                )
            }
        }
    }
    if (hostWidgetIds.isNotEmpty()) HorizontalDivider(thickness = 2.dp)

    widgetManager.installedProviders.forEach { widgetProvider ->
        val widgetBindLauncher = rememberLauncherForActivityResult(RequestWidgetBind()) { id ->
            if (id != null) {
                widgetManager.bindAppWidgetIdIfAllowed(id, widgetProvider.provider)
                ++refreshToken
            } else Toast.makeText(context, "No widget id", Toast.LENGTH_LONG).show()
        }

        fun add() =
            widgetBindLauncher.launch(appWidgetHost.allocateAppWidgetId() to widgetProvider.provider)

        Spacer(Modifier.height(16.dp))
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            widgetProvider.loadDescription(context).toString()
        } else "Widget"
        val dpi = context.resources.displayMetrics.densityDpi
        Image(
            painter = rememberDrawablePainter(widgetProvider.loadPreviewImage(context, dpi)),
            contentDescription = description,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.large)
                .clickable { add() },
        )
        TextButton(
            onClick = { add() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Add") }
    }
}

class RequestWidgetBind : ActivityResultContract<Pair<Int, ComponentName>, Int?>() {
    override fun createIntent(context: Context, input: Pair<Int, ComponentName>): Intent {
        val (id, provider) = input
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, Process.myUserHandle())
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Int? {
        return intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1).takeIf { it != -1 }
    }
}