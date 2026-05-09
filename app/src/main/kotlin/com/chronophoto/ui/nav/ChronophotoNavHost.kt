package com.chronophoto.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronophoto.ui.photo.PhotoDetailScreen
import com.chronophoto.ui.projects.capture.CaptureScreen
import com.chronophoto.ui.projects.create.NewProjectScreen
import com.chronophoto.ui.projects.detail.ProjectDetailScreen
import com.chronophoto.ui.projects.list.ProjectListScreen

@Composable
fun ChronophotoNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.PROJECTS) {
        composable(Routes.PROJECTS) {
            ProjectListScreen(
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) },
            )
        }
        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.projectDetail(id))
                },
            )
        }
        composable(
            route = Routes.PROJECT_DETAIL_ROUTE,
            arguments = listOf(navArgument(Routes.PROJECT_DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.PROJECT_DETAIL_ARG) ?: return@composable
            ProjectDetailScreen(
                projectId = id,
                onBack = { navController.popBackStack() },
                onCapture = { navController.navigate(Routes.capture(id)) },
                onOpenPhoto = { photoId -> navController.navigate(Routes.photoDetail(photoId)) },
            )
        }
        composable(
            route = Routes.CAPTURE_ROUTE,
            arguments = listOf(navArgument(Routes.CAPTURE_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.CAPTURE_ARG) ?: return@composable
            CaptureScreen(projectId = id, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PHOTO_DETAIL_ROUTE,
            arguments = listOf(navArgument(Routes.PHOTO_DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.PHOTO_DETAIL_ARG) ?: return@composable
            PhotoDetailScreen(photoId = id, onBack = { navController.popBackStack() })
        }
    }
}
