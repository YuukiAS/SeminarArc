package com.yuukias.seminararc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuukias.seminararc.ui.detail.SeminarDetailScreen
import com.yuukias.seminararc.ui.detail.SeminarDetailViewModel
import com.yuukias.seminararc.ui.editor.SeminarEditorScreen
import com.yuukias.seminararc.ui.editor.SeminarEditorViewModel
import com.yuukias.seminararc.ui.library.SeminarLibraryScreen
import com.yuukias.seminararc.ui.library.SeminarLibraryViewModel
import com.yuukias.seminararc.ui.session.ActiveSessionScreen
import com.yuukias.seminararc.ui.session.ActiveSessionViewModel

@Composable
fun SeminarNavHost(
    openActiveSeminarId: Long? = null,
    onOpenActiveSeminarConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(openActiveSeminarId) {
        val seminarId = openActiveSeminarId ?: return@LaunchedEffect
        navController.navigate(ActiveSessionRoute(seminarId)) {
            launchSingleTop = true
        }
        onOpenActiveSeminarConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = SeminarLibraryRoute,
    ) {
        composable<SeminarLibraryRoute> {
            val viewModel: SeminarLibraryViewModel = hiltViewModel()
            SeminarLibraryScreen(
                viewModel = viewModel,
                onCreateSeminar = { navController.navigate(SeminarEditorRoute()) },
                onOpenSeminar = { navController.navigate(SeminarDetailRoute(it)) },
            )
        }
        composable<SeminarEditorRoute> {
            val viewModel: SeminarEditorViewModel = hiltViewModel()
            SeminarEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { seminarId ->
                    navController.navigate(SeminarDetailRoute(seminarId)) {
                        popUpTo(SeminarLibraryRoute) { inclusive = false }
                    }
                },
            )
        }
        composable<SeminarDetailRoute> {
            val viewModel: SeminarDetailViewModel = hiltViewModel()
            SeminarDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEdit = { seminarId -> navController.navigate(SeminarEditorRoute(seminarId)) },
                onOpenActiveSession = { seminarId ->
                    navController.navigate(ActiveSessionRoute(seminarId)) {
                        launchSingleTop = true
                    }
                },
                onDeleted = {
                    navController.navigate(SeminarLibraryRoute) {
                        popUpTo(SeminarLibraryRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<ActiveSessionRoute> {
            val viewModel: ActiveSessionViewModel = hiltViewModel()
            ActiveSessionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { seminarId ->
                    navController.navigate(SeminarDetailRoute(seminarId)) {
                        popUpTo(SeminarLibraryRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToActiveSession = { seminarId ->
                    navController.navigate(ActiveSessionRoute(seminarId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
