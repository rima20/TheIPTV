package com.example.theiptv.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.ui.browse.BrowseScreen
import com.example.theiptv.ui.browse.BrowseViewModel
import com.example.theiptv.ui.components.LoadingBox
import com.example.theiptv.ui.components.NavigationSoundHost
import com.example.theiptv.ui.detail.MovieDetailScreen
import com.example.theiptv.ui.detail.MovieDetailViewModel
import com.example.theiptv.ui.detail.SeriesDetailScreen
import com.example.theiptv.ui.detail.SeriesDetailViewModel
import com.example.theiptv.ui.login.LoginScreen
import com.example.theiptv.ui.login.LoginViewModel
import com.example.theiptv.ui.player.PlayerScreen
import com.example.theiptv.ui.providers.ProvidersScreen
import com.example.theiptv.ui.providers.ProvidersViewModel
import com.example.theiptv.ui.search.SearchScreen
import com.example.theiptv.ui.search.SearchViewModel

@Composable
fun TheIptvApp() {
    val context = LocalContext.current
    val app = context.app()
    var startDestination by remember { mutableStateOf<String?>(null) }
    ConfirmExitOnBack()

    LaunchedEffect(Unit) {
        val snapshot = app.credentialsStore.loadAll()
        startDestination = when {
            snapshot.providers.isEmpty() -> "login"
            else -> {
                val active = snapshot.active
                if (active != null && app.repository.login(active.credentials) is ApiResult.Ok) {
                    "browse"
                } else {
                    "providers"
                }
            }
        }
    }

    val destination = startDestination
    if (destination == null) {
        NavigationSoundHost {
            LoadingBox("Connecting…")
        }
        return
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    fun goToBrowse() {
        navController.navigate("browse") {
            popUpTo(0) { inclusive = true }
        }
    }

    NavigationSoundHost(enabled = currentRoute != "player") {
    NavHost(navController = navController, startDestination = destination) {
        composable("login") {
            val vm: LoginViewModel = viewModel()
            LoginScreen(
                viewModel = vm,
                onLoggedIn = { goToBrowse() },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("providers") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("providers") {
            val vm: ProvidersViewModel = viewModel()
            ProvidersScreen(
                viewModel = vm,
                onConnected = { goToBrowse() },
                onAddProvider = { navController.navigate("login") }
            )
        }
        composable("browse") {
            val vm: BrowseViewModel = viewModel()
            BrowseScreen(
                viewModel = vm,
                onPlay = { item ->
                    app.repository.play(item)
                    navController.navigate("player")
                },
                onOpenSeries = { item ->
                    navController.navigate("series/${item.id}")
                },
                onOpenMovie = { item ->
                    navController.navigate("movie/${item.id}")
                },
                onSearch = { navController.navigate("search") },
                onGuide = { vm.onOpenGuide() },
                onPlayRequest = { request ->
                    app.repository.pendingPlayback = request
                    navController.navigate("player")
                },
                onSessionLost = {
                    navController.navigate("providers") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "series/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) {
            val vm: SeriesDetailViewModel = viewModel()
            SeriesDetailScreen(
                viewModel = vm,
                onPlay = { request ->
                    app.repository.pendingPlayback = request
                    navController.navigate("player")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "movie/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) {
            val vm: MovieDetailViewModel = viewModel()
            MovieDetailScreen(
                viewModel = vm,
                onPlay = { request ->
                    app.repository.pendingPlayback = request
                    navController.navigate("player")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("search") {
            val vm: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = vm,
                onPlay = { item ->
                    app.repository.play(item)
                    navController.navigate("player")
                },
                onOpenSeries = { item ->
                    navController.navigate("series/${item.id}")
                },
                onOpenMovie = { item ->
                    navController.navigate("movie/${item.id}")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("player") {
            PlayerScreen(
                request = app.repository.pendingPlayback,
                onBack = { navController.popBackStack() }
            )
        }
    }
    }
}

@Composable
private fun ConfirmExitOnBack() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2_000L) {
            activity?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, "Press back again to exit app", Toast.LENGTH_SHORT).show()
        }
    }
}
