package dev.jorge.flacbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jorge.flacbox.ui.App
import dev.jorge.flacbox.ui.theme.FlacBoxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlacBoxTheme {
                App()
            }
        }
    }
}
