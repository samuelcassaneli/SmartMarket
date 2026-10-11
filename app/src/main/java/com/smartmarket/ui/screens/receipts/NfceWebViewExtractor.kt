package com.smartmarket.ui.screens.receipts

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartmarket.domain.nfce.NfceException
import com.smartmarket.domain.nfce.NfceKey
import com.smartmarket.domain.nfce.NfceReceipt
import com.smartmarket.domain.nfce.NfceService
import com.smartmarket.domain.nfce.SefazDirectory
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Fallback em WebView VISÍVEL para páginas da SEFAZ com captcha ou feitas em JavaScript.
 * O usuário resolve o que for preciso; quando a página tem `#tabResult`, extraímos sozinhos
 * (`document.documentElement.outerHTML` -> mesmo parser). Senão, o botão "Já carregou, importar".
 *
 * @param clipboardKey se informado, a chave é copiada para a área de transferência (consulta por chave).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NfceWebViewExtractor(
    url: String,
    expectedKey: NfceKey?,
    onExtracted: (NfceReceipt) -> Unit,
    onDismiss: () -> Unit,
    clipboardKey: String? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val currentOnExtracted by rememberUpdatedState(onExtracted)
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(clipboardKey) {
        if (clipboardKey != null) {
            clipboard.setText(AnnotatedString(clipboardKey))
            message = "Chave copiada. Cole no campo da consulta e confirme a verificação."
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    /** Lê o HTML atual e tenta interpretar. [manual] = o usuário pediu (mostra o erro). */
    fun tryExtract(manual: Boolean) {
        if (done) return
        // Só lê o HTML de páginas oficiais da SEFAZ (.gov.br)
        if (!SefazDirectory.isOfficialUrl(webView.url)) {
            if (manual) message = "Esta página não é de um site oficial da SEFAZ."
            return
        }
        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
            if (done) return@evaluateJavascript
            val html = runCatching { Json.decodeFromString(String.serializer().nullable, result ?: "null") }.getOrNull()
            if (html.isNullOrBlank()) {
                if (manual) message = "Não consegui ler a página. Espere carregar e tente de novo."
                return@evaluateJavascript
            }
            if (!manual && !html.contains("tabResult")) return@evaluateJavascript
            try {
                val receipt = NfceService.parseHtml(html, webView.url ?: url, expectedKey)
                done = true
                currentOnExtracted(receipt)
            } catch (e: NfceException) {
                if (manual) message = e.message
            } catch (e: Exception) {
                if (manual) message = "Não reconhecemos o formato desta página."
            }
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            /** Bloqueia navegação (quadro principal) para fora dos hosts oficiais .gov.br. */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return true
                if (!req.isForMainFrame) return false
                val target = req.url ?: return true
                val scheme = target.scheme?.lowercase()
                val allowed = (scheme == "http" || scheme == "https") && SefazDirectory.isOfficialHost(target.host)
                if (!allowed) message = "Bloqueamos um link para fora do site da SEFAZ."
                return !allowed
            }

            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                loading = true
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                loading = false
                tryExtract(manual = false)
            }
        }
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress / 100f
            }
        }
        if (SefazDirectory.isOfficialUrl(url)) {
            webView.loadUrl(url)
        } else {
            loading = false
            message = "Endereço fora dos sites oficiais da SEFAZ."
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Páginas que montam a tabela via AJAX depois do onPageFinished
    LaunchedEffect(webView) {
        while (!done) {
            delay(2500)
            tryExtract(manual = false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ScreenScaffold(
                topBar = {
                    Column {
                        AppTopBar(
                            title = "Consulta na SEFAZ",
                            subtitle = webView.url ?: url,
                            actions = {
                                IconButton(onClick = { webView.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Recarregar")
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                                }
                            }
                        )
                        if (loading) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                        ) {
                            Text(
                                message ?: "Se aparecer uma verificação, resolva. Quando a nota aparecer, importamos sozinhos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { tryExtract(manual = true) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Já carregou, importar")
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}
