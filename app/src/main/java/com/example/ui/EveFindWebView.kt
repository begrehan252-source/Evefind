package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.R
import java.io.File

const val MAIN_WEBSITE_URL = "https://onlineeve.in"
const val WEBSITE_HOST = "onlineeve.in"

data class JsDialogState(
    val message: String = "",
    val defaultValue: String = "",
    val type: DialogType = DialogType.ALERT,
    val result: Any? = null
)

enum class DialogType { ALERT, CONFIRM, PROMPT }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EveFindWebViewContainer(
    modifier: Modifier = Modifier,
    onFileChooserRequested: (Intent, ValueCallback<Array<Uri>>) -> Unit,
    onRequestCameraPermission: (() -> Unit) -> Unit,
    onRequestLocationPermission: (() -> Unit) -> Unit,
    hasCameraPermission: () -> Boolean,
    hasLocationPermission: () -> Boolean
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isErrorState by remember { mutableStateOf(false) }

    // Dialog states for JS alert/confirm/prompt
    var dialogState by remember { mutableStateOf<JsDialogState?>(null) }
    var promptInputText by remember { mutableStateOf("") }

    // Back handler for WebView back navigation
    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("evefind_webview_container")
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Use software layer rendering to avoid Mesa DRM rendernode queries in container environment
                    setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)

                    // Configure Cookie Manager
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // Configure WebSettings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        supportZoom()
                        builtInZoomControls = true
                        displayZoomControls = false
                        javaScriptCanOpenWindowsAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = settings.userAgentString + " EveFindAndroidApp/1.0"
                    }

                    // Handle file downloads
                    setDownloadListener { url, _, _, _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Cannot open download link", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // WebViewClient for navigation and load states
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            isErrorState = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            CookieManager.getInstance().flush()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isErrorState = true
                                isLoading = false
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false

                            // Handle special schemes (tel, mailto, whatsapp, intent, geo, market)
                            if (url.startsWith("tel:") ||
                                url.startsWith("mailto:") ||
                                url.startsWith("whatsapp:") ||
                                url.startsWith("intent:") ||
                                url.startsWith("geo:") ||
                                url.startsWith("market:")
                            ) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "No application found to open link", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }

                            // Keep onlineeve.in inside WebView
                            val host = request.url?.host
                            if (host != null && (host == WEBSITE_HOST || host.endsWith(".$WEBSITE_HOST"))) {
                                return false
                            }

                            // External links that are not onlineeve.in can be opened externally
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                ctx.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }

                    // WebChromeClient for dialogs, progress, location, camera, file chooser
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            progress = newProgress / 100f
                            if (newProgress == 100) {
                                isLoading = false
                            }
                        }

                        // JS Alert
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            dialogState = JsDialogState(
                                message = message ?: "",
                                type = DialogType.ALERT,
                                result = result
                            )
                            return true
                        }

                        // JS Confirm
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            dialogState = JsDialogState(
                                message = message ?: "",
                                type = DialogType.CONFIRM,
                                result = result
                            )
                            return true
                        }

                        // JS Prompt
                        override fun onJsPrompt(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult?
                        ): Boolean {
                            promptInputText = defaultValue ?: ""
                            dialogState = JsDialogState(
                                message = message ?: "",
                                defaultValue = defaultValue ?: "",
                                type = DialogType.PROMPT,
                                result = result
                            )
                            return true
                        }

                        // Geolocation Permissions
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            if (hasLocationPermission()) {
                                callback?.invoke(origin, true, false)
                            } else {
                                onRequestLocationPermission {
                                    val granted = hasLocationPermission()
                                    callback?.invoke(origin, granted, false)
                                }
                            }
                        }

                        // Web Permissions (Camera / Microphone)
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            if (request == null) return
                            val resources = request.resources
                            var needsCamera = false
                            for (res in resources) {
                                if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                    needsCamera = true
                                }
                            }

                            if (needsCamera && !hasCameraPermission()) {
                                onRequestCameraPermission {
                                    if (hasCameraPermission()) {
                                        request.grant(resources)
                                    } else {
                                        request.deny()
                                    }
                                }
                            } else {
                                request.grant(resources)
                            }
                        }

                        // File Chooser for File & Photo Uploads
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (filePathCallback == null) return false

                            val intentList = mutableListOf<Intent>()

                            // Create Camera Photo Intent with FileProvider Uri
                            try {
                                val photoFile = File.createTempFile(
                                    "EVEFIND_${System.currentTimeMillis()}_",
                                    ".jpg",
                                    ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                                )
                                val photoUri = FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.fileprovider",
                                    photoFile
                                )
                                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                                intentList.add(cameraIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // Document / Gallery Picker Intent
                            val contentIntent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }

                            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                                putExtra(Intent.EXTRA_INTENT, contentIntent)
                                putExtra(Intent.EXTRA_TITLE, "Select File or Take Photo")
                                if (intentList.isNotEmpty()) {
                                    putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toTypedArray())
                                }
                            }

                            onFileChooserRequested(chooserIntent, filePathCallback)
                            return true
                        }
                    }

                    loadUrl(MAIN_WEBSITE_URL)
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Progress Bar
        if (isLoading && !isErrorState) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Offline / Error State Screen
        AnimatedVisibility(
            visible = isErrorState,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Connection Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to connect to EveFind",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please check your internet connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            isErrorState = false
                            isLoading = true
                            webViewInstance?.reload()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Retry")
                    }
                }
            }
        }

        // JavaScript Dialogs Handler
        dialogState?.let { state ->
            AlertDialog(
                onDismissRequest = {
                    when (state.type) {
                        DialogType.ALERT -> (state.result as? JsResult)?.confirm()
                        DialogType.CONFIRM -> (state.result as? JsResult)?.cancel()
                        DialogType.PROMPT -> (state.result as? JsPromptResult)?.cancel()
                    }
                    dialogState = null
                },
                title = { Text(text = stringResource(R.string.app_name)) },
                text = {
                    Column {
                        Text(text = state.message)
                        if (state.type == DialogType.PROMPT) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = promptInputText,
                                onValueChange = { promptInputText = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (state.type) {
                                DialogType.ALERT -> (state.result as? JsResult)?.confirm()
                                DialogType.CONFIRM -> (state.result as? JsResult)?.confirm()
                                DialogType.PROMPT -> (state.result as? JsPromptResult)?.confirm(promptInputText)
                            }
                            dialogState = null
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = if (state.type != DialogType.ALERT) {
                    {
                        TextButton(
                            onClick = {
                                when (state.type) {
                                    DialogType.CONFIRM -> (state.result as? JsResult)?.cancel()
                                    DialogType.PROMPT -> (state.result as? JsPromptResult)?.cancel()
                                    else -> {}
                                }
                                dialogState = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                } else null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }
}
