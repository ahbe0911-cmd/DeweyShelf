package ir.deweyshelf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.deweyshelf.app.core.DeweyTheme
import ir.deweyshelf.app.presentation.DeweyApp
import ir.deweyshelf.app.presentation.DeweyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as DeweyApplication
            val viewModel: DeweyViewModel = viewModel(
                factory = DeweyViewModel.factory(application.repository),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DeweyTheme {
                    DeweyApp(viewModel = viewModel)
                }
            }
        }
    }
}

