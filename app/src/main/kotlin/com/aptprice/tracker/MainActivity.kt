package com.aptprice.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aptprice.tracker.presentation.navigation.AppNavigation
import com.aptprice.tracker.ui.theme.AptPriceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AptPriceTheme {
                AppNavigation()
            }
        }
    }
}
