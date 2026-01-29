package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import app.rive.runtime.example.compose.RewardsBottomPanel
import kotlinx.coroutines.flow.map

enum class WinKind(val enumValue: String) {
    COIN("Coin"),
    GEM("Gem");

    fun label(): String = "${enumValue}s"

    companion object {
        fun from(enumValue: String): WinKind = entries.first { it.enumValue == enumValue }
    }
}

class ComposeDataBindingActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = "#30202F".toColorInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(backgroundColor),
            navigationBarStyle = SystemBarStyle.dark(backgroundColor)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val riveFileResult =
                rememberRiveFile(RiveFileSource.RawRes.from(R.raw.rewards_demo), riveWorker)
            var showBottomPanel by remember { mutableStateOf(false) }

            Scaffold(
                containerColor = Color(backgroundColor),
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        showBottomPanel = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Data binding options"
                        )
                    }
                }) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (riveFileResult) {
                        is Result.Loading -> LoadingIndicator()
                        is Result.Error -> ErrorMessage(riveFileResult.throwable)
                        is Result.Success -> {
                            val riveFile = riveFileResult.value
                            val vmi = rememberViewModelInstance(riveFile)
                            val lives by vmi.getNumberFlow("Energy_Bar/Lives")
                                .collectAsStateWithLifecycle(0f)
                            val energy by vmi.getNumberFlow("Energy_Bar/Energy_Bar")
                                .collectAsStateWithLifecycle(0f)
                            val coins by vmi.getNumberFlow("Coin/Item_Value")
                                .collectAsStateWithLifecycle(0f)
                            val gems by vmi.getNumberFlow("Gem/Item_Value")
                                .collectAsStateWithLifecycle(0f)
                            val winValue by vmi.getNumberFlow("Price_Value")
                                .collectAsStateWithLifecycle(0f)
                            val winKind by vmi.getEnumFlow("Item_Selection/Item_Selection")
                                .map { WinKind.from(it) }
                                .collectAsStateWithLifecycle(WinKind.COIN)

                            Column {
                                Rive(
                                    file = riveFile,
                                    viewModelInstance = vmi,
                                    fit = Fit.Layout(),
                                    modifier = Modifier.semantics {
                                        contentDescription = "Rive UI - Rewards Demo"
                                    }
                                )

                                if (showBottomPanel) {
                                    RewardsBottomPanel(
                                        lives,
                                        energy,
                                        coins,
                                        gems,
                                        winValue,
                                        winKind,
                                        onDismiss = { showBottomPanel = false },
                                        onVmiSetNumber = { property, value ->
                                            vmi.setNumber(property, value)
                                        },
                                        onVmiSetColor = { property, value ->
                                            vmi.setColor(property, value)
                                        },
                                        onVmiSetEnum = { property, value ->
                                            vmi.setEnum(property, value)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
