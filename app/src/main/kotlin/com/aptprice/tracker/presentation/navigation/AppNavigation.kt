package com.aptprice.tracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aptprice.tracker.presentation.feed.FeedScreen

object Routes {
    const val FEED = "feed"

    /** Step 4 에서 붙일 단지 상세. 인자는 단지+평형 키다. */
    const val DETAIL = "detail/{complexAreaKey}"

    fun detailOf(complexAreaKey: String): String = "detail/$complexAreaKey"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.FEED) {
        composable(Routes.FEED) {
            FeedScreen(
                onDealClick = { _ ->
                    // Step 4 에서 상세 화면으로 연결한다.
                },
            )
        }
    }
}
