package com.dexform.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dexform.app.ui.browser.BrowserScreen
import com.dexform.app.ui.gallery.AlbumDetailScreen
import com.dexform.app.ui.gallery.AlbumsScreen
import com.dexform.app.ui.gallery.PhotosScreen
import com.dexform.app.ui.profile.AiChatScreen
import com.dexform.app.ui.profile.ProfileScreen
import com.dexform.app.ui.settings.SettingsScreen
import com.dexform.app.ui.theme.DexFormTheme
import com.dexform.app.ui.tools.ToolsScreen

import androidx.compose.ui.text.style.TextOverflow

sealed class BottomNavItem(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    object Photos : BottomNavItem("photos", R.string.tab_photos, Icons.Outlined.GridView)
    object Albums : BottomNavItem("albums", R.string.tab_albums, Icons.Outlined.Folder)
    object Tools : BottomNavItem("tools", R.string.tab_tools, Icons.Outlined.Tune)
    object Browser : BottomNavItem("browser", R.string.tab_browser, Icons.Outlined.Public)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DexFormTheme {
                DexFormApp()
            }
        }
    }
}

@Composable
fun DexFormApp() {
    val navController = rememberNavController()
    val navItems = listOf(
        BottomNavItem.Photos,
        BottomNavItem.Albums,
        BottomNavItem.Tools,
        BottomNavItem.Browser
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomBarVisible = currentRoute in navItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = stringResource(item.titleRes),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.titleRes),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Photos.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Photos.route) {
                PhotosScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onOpenBrowser = { navController.navigate(BottomNavItem.Browser.route) }
                )
            }
            composable(BottomNavItem.Albums.route) {
                AlbumsScreen(
                    onAlbumClick = { bucketId, name ->
                        navController.navigate("album_detail/$bucketId/$name")
                    },
                    onOpenTools = { navController.navigate(BottomNavItem.Tools.route) },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable(
                route = "album_detail/{bucketId}/{albumName}",
                arguments = listOf(
                    navArgument("bucketId") { type = NavType.LongType },
                    navArgument("albumName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val bucketId = backStackEntry.arguments?.getLong("bucketId") ?: 0L
                val albumName = backStackEntry.arguments?.getString("albumName") ?: "DexForm"
                AlbumDetailScreen(
                    bucketId = bucketId,
                    albumName = albumName,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(BottomNavItem.Tools.route) {
                ToolsScreen(
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable(BottomNavItem.Browser.route) {
                BrowserScreen(
                    onNavigateToProfile = { navController.navigate("profile") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAiChat = { navController.navigate("ai_chat") }
                )
            }
            composable("ai_chat") {
                AiChatScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
