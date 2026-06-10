package com.santoso.tech.ui.news

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleReaderScreen(
    url: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Memuat...") }
    var currentUrl by remember { mutableStateOf(url) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Handle system back button
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    val progressFloat by animateFloatAsState(
        targetValue = loadingProgress / 100f,
        animationSpec = tween(200),
        label = "progress"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                // Top bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                pageTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                currentUrl.substringAfter("://").substringBefore("/"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (canGoBack) webView?.goBack() else onBackClick()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Close button (always goes back to news list)
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                // Refresh
                                DropdownMenuItem(
                                    text = { Text("Muat Ulang") },
                                    onClick = {
                                        showMenu = false
                                        webView?.reload()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                    }
                                )
                                // Share
                                DropdownMenuItem(
                                    text = { Text("Bagikan") },
                                    onClick = {
                                        showMenu = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$pageTitle\n$currentUrl")
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Bagikan berita")
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                    }
                                )
                                // Open in browser
                                DropdownMenuItem(
                                    text = { Text("Buka di Browser") },
                                    onClick = {
                                        showMenu = false
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                            )
                                        } catch (_: Exception) {}
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInBrowser, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Loading progress bar
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            allowFileAccess = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Improve text readability
                            textZoom = 100
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, pageUrl, favicon)
                                isLoading = true
                                pageUrl?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                super.onPageFinished(view, pageUrl)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                // Inject CSS to improve readability
                                view?.evaluateJavascript("""
                                    (function() {
                                        var meta = document.querySelector('meta[name="viewport"]');
                                        if (!meta) {
                                            meta = document.createElement('meta');
                                            meta.name = 'viewport';
                                            document.head.appendChild(meta);
                                        }
                                        meta.content = 'width=device-width, initial-scale=1.0';
                                    })();
                                """.trimIndent(), null)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                // Keep http/https in WebView, open others externally
                                return if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                    false
                                } else {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl)))
                                    } catch (_: Exception) {}
                                    true
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { if (it.isNotBlank()) pageTitle = it }
                            }
                        }

                        // Load the article URL
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { /* WebView already loaded */ }
            )

            // Overlay loading shimmer when first loading
            AnimatedVisibility(
                visible = isLoading && loadingProgress < 30,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            "Memuat artikel...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
