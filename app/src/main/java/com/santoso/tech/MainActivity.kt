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
import com.santoso.tech.ui.theme.CyrptoInfoTheme
import dagger.hilt.android.AndroidEntryPoint

// Bottom nav destinations
sealed class BottomNavItem(val route: String, val label: String) {
    object Market   : BottomNavItem("market",   "Pasar")
    object Favorite : BottomNavItem("favorite", "Favorit")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyrptoInfoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1117)) {
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
    val darkBg = Color(0xFF0D1117)
    val navBg  = Color(0xFF161B22)
    val accent = Color(0xFF58A6FF)

    // Track current route to know when to show bottom nav
    var currentRoute by remember { mutableStateOf("market") }
    navController.addOnDestinationChangedListener { _, destination, _ ->
        currentRoute = destination.route ?: "market"
    }

    val showBottomBar = currentRoute == "market" || currentRoute == "favorite"

    Scaffold(
        containerColor = darkBg,
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
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFF21262D)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "market",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("market") {
                val viewModel: MarketViewModel = hiltViewModel()
                MarketScreen(
                    viewModel = viewModel,
                    onPairClick = { instId ->
                        navController.navigate("detail/$instId")
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
        }
    }
}
