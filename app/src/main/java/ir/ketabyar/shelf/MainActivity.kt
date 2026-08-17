package ir.ketabyar.shelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ir.ketabyar.shelf.ui.KetabYarRoot
import ir.ketabyar.shelf.ui.KetabYarTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { KetabYarTheme { KetabYarRoot() } }
    }
}

