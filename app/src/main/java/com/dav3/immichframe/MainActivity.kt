package com.dav3.immichframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dav3.immichframe.ui.nav.ImmichNavHost
import com.dav3.immichframe.ui.theme.ImmichFrameTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImmichFrameTheme {
                ImmichNavHost()
            }
        }
    }
}
