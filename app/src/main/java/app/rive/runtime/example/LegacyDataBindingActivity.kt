package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.RiveLog
import app.rive.runtime.example.compose.RewardsBottomPanel
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.ViewModelInstance
import kotlinx.coroutines.flow.map

class LegacyDataBindingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = "#30202F".toColorInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(backgroundColor),
            navigationBarStyle = SystemBarStyle.dark(backgroundColor)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            var viewModelInstance by remember { mutableStateOf<ViewModelInstance?>(null) }
            var showBottomPanel by remember { mutableStateOf(false) }

            Scaffold(
                containerColor = Color(backgroundColor),
                floatingActionButton = {
                    if (viewModelInstance != null) {
                        FloatingActionButton(onClick = { showBottomPanel = true }) {
                            Icon(Icons.Default.List, "Data binding options")
                        }
                    }
                }) { innerPadding ->
                Column(Modifier.padding(innerPadding)) {
                    AndroidView({ context ->
                        RiveAnimationView(context).also { view ->
                            view.setRiveResource(
                                resId = R.raw.rewards_demo,
                                autoBind = true,
                                fit = Fit.LAYOUT
                            )
                            view.layoutScaleFactor = 1f

                            // Acquire the view model instance from the auto-bound Rive file
                            viewModelInstance = view.stateMachines.first().viewModelInstance!!
                        }
                    })

                    if (showBottomPanel && viewModelInstance != null) {
                        val vmi = viewModelInstance!!
                        val lives by vmi.getNumberProperty("Energy_Bar/Lives").valueFlow
                            .collectAsStateWithLifecycle(0f)
                        val energy by vmi.getNumberProperty("Energy_Bar/Energy_Bar").valueFlow
                            .collectAsStateWithLifecycle(0f)
                        val coins by vmi.getNumberProperty("Coin/Item_Value").valueFlow
                            .collectAsStateWithLifecycle(0f)
                        val gems by vmi.getNumberProperty("Gem/Item_Value").valueFlow
                            .collectAsStateWithLifecycle(0f)
                        val winValue by vmi.getNumberProperty("Price_Value").valueFlow
                            .collectAsStateWithLifecycle(0f)
                        val winKind by vmi.getEnumProperty("Item_Selection/Item_Selection")
                            .valueFlow.map { WinKind.from(it) }
                            .collectAsStateWithLifecycle(WinKind.COIN)

                        RewardsBottomPanel(
                            lives,
                            energy,
                            coins,
                            gems,
                            winValue,
                            winKind,
                            onDismiss = { showBottomPanel = false },
                            onVmiSetNumber = { propertyName, newValue ->
                                vmi.getNumberProperty(propertyName).value = newValue
                            },
                            onVmiSetColor = { propertyName, newColorInt ->
                                vmi.getColorProperty(propertyName).value =
                                    newColorInt
                            },
                            onVmiSetEnum = { propertyName, newEnum ->
                                vmi.getEnumProperty(propertyName).value = newEnum
                            }
                        )
                    }
                }
            }
        }
    }
}
