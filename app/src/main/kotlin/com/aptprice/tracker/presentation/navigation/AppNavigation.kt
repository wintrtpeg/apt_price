package com.aptprice.tracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aptprice.tracker.domain.model.ComplexAreaKey
import com.aptprice.tracker.presentation.detail.DetailScreen
import com.aptprice.tracker.presentation.feed.FeedScreen
import com.aptprice.tracker.presentation.settings.SettingsScreen

object Routes {
    const val FEED = "feed"
    const val SETTINGS = "settings"

    /** 단지 상세. 인자는 URL 안전 Base64 로 인코딩된 단지+평형 키다. */
    const val ARG_COMPLEX_AREA_KEY = "complexAreaKey"
    const val DETAIL = "detail/{$ARG_COMPLEX_AREA_KEY}"

    fun detailOf(complexAreaKey: String): String =
        "detail/${ComplexAreaKey(complexAreaKey).encode()}"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.FEED) {
        composable(Routes.FEED) {
            FeedScreen(
                onDealClick = { key -> navController.navigate(Routes.detailOf(key)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_COMPLEX_AREA_KEY) { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
