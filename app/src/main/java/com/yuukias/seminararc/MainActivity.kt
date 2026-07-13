package com.yuukias.seminararc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.yuukias.seminararc.ui.navigation.SeminarNavHost
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeminarArcAppContent()
        }
    }
}

@Composable
private fun SeminarArcAppContent() {
    SeminarArcTheme {
        Surface {
            SeminarNavHost()
        }
    }
}
