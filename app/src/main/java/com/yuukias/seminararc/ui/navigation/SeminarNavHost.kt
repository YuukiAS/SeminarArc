package com.yuukias.seminararc.ui.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun SeminarNavHost() {
    val navController = rememberNavController()

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
                onDeleted = {
                    navController.navigate(SeminarLibraryRoute) {
                        popUpTo(SeminarLibraryRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
