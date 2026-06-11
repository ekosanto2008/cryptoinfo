package com.santoso.tech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.santoso.tech.ui.detail.DetailScreen
import com.santoso.tech.ui.detail.DetailViewModel
import com.santoso.tech.ui.favorite.FavoriteScreen
import com.santoso.tech.ui.favorite.FavoriteViewModel
import com.santoso.tech.ui.market.MarketScreen
import com.santoso.tech.ui.market.MarketViewModel
import com.santoso.tech.ui.news.ArticleReaderScreen
import com.santoso.tech.ui.splash.SplashScreen
import com.santoso.tech.ui.theme.CyrptoInfoTheme
import dagger.hilt.android.AndroidEntryPoint

import com.santoso.tech.data.repository.SettingsRepository
import com.santoso.tech.data.repository.ThemeMode
import javax.inject.Inject

// Bottom nav destinations
sealed class BottomNavItem(val route: String, val label: String) {
    object Market   : BottomNavItem("market",   "Pasar")
    object Favorite : BottomNavItem("favorite", "Favorit")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsState()
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CyrptoInfoTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val bgColor = MaterialTheme.colorScheme.background
    val navBg  = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary

    // Track current route to know when to show bottom nav
    var currentRoute by remember { mutableStateOf("splash") }
    navController.addOnDestinationChangedListener { _, destination, _ ->
        currentRoute = destination.route ?: "market"
    }

    val showBottomBar = currentRoute == "market" || currentRoute == "favorite"

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = navBg,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(BottomNavItem.Market, BottomNavItem.Favorite)
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo("market") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                when (item) {
                                    BottomNavItem.Market -> Icon(
                                        if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                                        contentDescription = item.label
                                    )
                                    BottomNavItem.Favorite -> Icon(
                                        if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = item.label
                                    )
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate("market") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("market") {
                val viewModel: MarketViewModel = hiltViewModel()
                MarketScreen(
                    viewModel = viewModel,
                    onPairClick = { instId ->
                        navController.navigate("detail/$instId")
                    },
                    onArticleClick = { url ->
                        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("article/$encoded")
                    }
                )
            }

            composable("favorite") {
                val viewModel: FavoriteViewModel = hiltViewModel()
                FavoriteScreen(
                    viewModel = viewModel,
                    onCoinClick = { instId ->
                        navController.navigate("detail/$instId")
                    }
                )
            }

            composable(
                route = "detail/{instId}",
                arguments = listOf(navArgument("instId") { type = NavType.StringType })
            ) {
                val viewModel: DetailViewModel = hiltViewModel()
                DetailScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "article/{encodedUrl}",
                arguments = listOf(navArgument("encodedUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("encodedUrl") ?: ""
                val articleUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                ArticleReaderScreen(
                    url = articleUrl,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
