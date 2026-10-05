package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.stack.mutableStateStackOf
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.getHtml
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

// ANZ -->
class WebViewWindow(webContent: WebContent, val navigator: WebViewNavigator) { // ANZ
    var state by mutableStateOf(WebViewState(webContent))
    var popupMessage: Message? = null
        private set
    var webView: WebView? = null

    constructor(popupMessage: Message, navigator: WebViewNavigator) : this(WebContent.NavigatorOnly, navigator) {
        this.popupMessage = popupMessage
    }
}
// ANZ <--

@Composable
fun WebViewScreenContent(
    onNavigateUp: () -> Unit,
    initialTitle: String?,
    url: String,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearCookies: (String) -> Unit,
    headers: Map<String, String> = emptyMap(),
    onUrlChange: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    // ANZ -->
    val windowStack = remember {
        mutableStateStackOf(
            WebViewWindow(
                WebContent.Url(url = url, additionalHttpHeaders = headers),
                WebViewNavigator(coroutineScope),
            ),
        )
    }

    val currentWindow = windowStack.lastItemOrNull!!
    val navigator = currentWindow.navigator
    // ANZ <--

    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(url) }
    var showCloudflareHelp by remember { mutableStateOf(false) }
    // ANZ -->
    var isActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { isActive = false }
    }
    // ANZ <--

    val webClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                android.webkit.CookieManager.getInstance().flush()
                // ANZ -->
                val hasClearance = android.webkit.CookieManager.getInstance().getCookie(url)?.contains("cf_clearance") == true
                if (hasClearance) {
                    NetworkHelper.lastSolveAtMs = System.currentTimeMillis()
                    url?.toHttpUrlOrNull()?.let { httpUrl ->
                        NetworkHelper.rememberSolveUa(httpUrl.host, view.settings.userAgentString)
                    }
                }
                // ANZ <--
                scope.launch {
                    val html = view.getHtml()
                    showCloudflareHelp = "window._cf_chl_opt" in html || "Ray ID is" in html
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                request?.let {
                    // Don't attempt to open blobs as webpages
                    if (it.url.toString().startsWith("blob:http")) {
                        return false
                    }

                    // Ignore intents urls
                    if (it.url.toString().startsWith("intent://")) {
                        return true
                    }

                    // Continue with request, but with custom headers
                    view?.loadUrl(it.url.toString(), headers)
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    }

    // ANZ -->
    val webChromeClient = remember {
        object : AccompanistWebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (isUserGesture) {
                    windowStack.push(WebViewWindow(resultMsg, WebViewNavigator(coroutineScope)))
                    return true
                }
                return false
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    fun initializePopup(webView: WebView, message: Message): WebView {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()
        return webView
    }

    val popState = remember<() -> Unit> {
        {
            if (windowStack.size == 1) {
                onNavigateUp()
            } else {
                windowStack.pop()
            }
        }
    }

    BackHandler(windowStack.size > 1, popState)
    // ANZ <--

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = currentWindow.state.pageTitle ?: initialTitle,
                        subtitle = currentUrl,
                        navigateUp = onNavigateUp,
                        navigationIcon = Icons.Outlined.Close,
                        actions = {
                            AppBarActions(
                                buildList<AppBar.AppBarAction> {
                                    // ANZ -->
                                    if (windowStack.size > 1) {
                                        add(
                                            AppBar.Action(
                                                title = stringResource(AMR.strings.action_webview_close_tab),
                                                icon = Icons.Outlined.Close,
                                                onClick = popState,
                                            ),
                                        )
                                    }
                                    // ANZ <--
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_webview_back),
                                            icon = Icons.Outlined.ArrowBack,
                                            onClick = {
                                                if (navigator.canGoBack) {
                                                    navigator.navigateBack()
                                                }
                                            },
                                            enabled = navigator.canGoBack,
                                        ),
                                    )
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_webview_forward),
                                            icon = Icons.Outlined.ArrowForward,
                                            onClick = {
                                                if (navigator.canGoForward) {
                                                    navigator.navigateForward()
                                                }
                                            },
                                            enabled = navigator.canGoForward,
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_webview_refresh),
                                            onClick = { navigator.reload() },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_share),
                                            onClick = { onShare(currentUrl) },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_open_in_browser),
                                            onClick = { onOpenInBrowser(currentUrl) },
                                        ),
                                    )
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_clear_cookies),
                                            onClick = { onClearCookies(currentUrl) },
                                        ),
                                    )
                                }.toPersistentList(),
                            )
                        },
                    )

                    if (showCloudflareHelp) {
                        Surface(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            WarningBanner(
                                textRes = MR.strings.information_cloudflare_help,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        uriHandler.openUri(
                                            "https://aniyomi.org/docs/guides/troubleshooting/#cloudflare",
                                        )
                                    },
                            )
                        }
                    }
                }
                when (val loadingState = currentWindow.state.loadingState) {
                    is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    is LoadingState.Loading -> LinearProgressIndicator(
                        progress = { (loadingState as? LoadingState.Loading)?.progress ?: 1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    else -> {}
                }
            }
        },
    ) { contentPadding ->
        // ANZ -->
        key(currentWindow) {
            WebView(
                state = currentWindow.state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                navigator = navigator,
                onCreated = { webView ->
                    webView.setDefaultSettings()

                    // Fix flickering issues
                    webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    webView.isDrawingCacheEnabled = false
                    webView.setBackgroundColor(0)

                    // Debug mode (chrome://inspect/#devices)
                    if (isDebugBuildType &&
                        0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                    ) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    headers["user-agent"]?.let {
                        webView.setUserAgent(it)
                    }
                },
                onDispose = { webView ->
                    val window = windowStack.items.find { it.webView == webView }
                    if (window == null) {
                        webView.destroy()
                    } else {
                        window.state.content = WebContent.NavigatorOnly
                    }
                },
                client = webClient,
                chromeClient = webChromeClient,
                factory = { context ->
                    currentWindow.webView
                        ?: WebView(context).also { webView ->
                            currentWindow.webView = webView
                            currentWindow.popupMessage?.let {
                                initializePopup(webView, it)
                            }
                        }
                },
            )
        }
        // ANZ <--
    }
}
