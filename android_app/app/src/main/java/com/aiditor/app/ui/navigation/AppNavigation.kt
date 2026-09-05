package com.aiditor.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiditor.app.data.model.Project
import com.aiditor.app.data.repository.ProjectRepository
import com.aiditor.app.ui.screens.mainmenu.MainMenuScreen
import com.aiditor.app.ui.screens.mainmenu.MainMenuViewModel
import com.aiditor.app.ui.screens.workspace.WorkspaceScreen
import com.aiditor.app.ui.screens.workspace.WorkspaceViewModel

sealed class Screen(val route: String) {
    object MainMenu : Screen("main_menu")
    object Workspace : Screen("workspace/{projectId}") {
        fun createRoute(projectId: String) = "workspace/$projectId"
    }
}

@Composable
fun AiditorNavGraph(
    navController: NavHostController = rememberNavController(),
    mainMenuViewModel: MainMenuViewModel = viewModel(),
    workspaceViewModel: WorkspaceViewModel = viewModel()
) {
    var selectedProject by remember { mutableStateOf<Project?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu.route
    ) {
        // Screen 1: Main Menu (Appears on launch)
        composable(
            route = Screen.MainMenu.route,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(250))
            }
        ) {
            MainMenuScreen(
                viewModel = mainMenuViewModel,
                onProjectSelect = { project ->
                    selectedProject = project
                    navController.navigate(Screen.Workspace.createRoute(project.id))
                }
            )
        }

        // Screen 2: Video Editing Workspace
        composable(
            route = Screen.Workspace.route,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(250))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250))
            }
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val project = selectedProject ?: ProjectRepository.getDefaultProjects().find { it.id == projectId }
                ?: ProjectRepository.getDefaultProjects().first()

            WorkspaceScreen(
                project = project,
                viewModel = workspaceViewModel,
                onBackToMainMenu = {
                    navController.popBackStack()
                }
            )
        }
    }
}
