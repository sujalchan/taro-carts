package nz.ac.aut.comp713.tarocartmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nz.ac.aut.comp713.tarocartmobile.ui.AllocationsScreen
import nz.ac.aut.comp713.tarocartmobile.ui.theme.TaroCartMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaroCartMobileTheme(dynamicColor = false) {
                AllocationsScreen()
            }
        }
    }
}
