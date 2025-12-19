package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveUI
import app.rive.rememberCommandQueue
import app.rive.rememberRiveFile
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.Fit
import java.util.Locale

enum class WinKind(val enumValue: String) {
    COIN("Coin"),
    GEM("Gem");

    fun label(): String = "${enumValue}s"
}

class ComposeDataBindingActivity : ComponentActivity() {
    @OptIn(ExperimentalRiveComposeAPI::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = "#30202F".toColorInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(backgroundColor),
            navigationBarStyle = SystemBarStyle.dark(backgroundColor)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val commandQueue = rememberCommandQueue()
            val riveFileResult =
                rememberRiveFile(RiveFileSource.RawRes.from(R.raw.rewards_demo), commandQueue)

            var showBottomSheet by remember { mutableStateOf(false) }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(backgroundColor),
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        showBottomSheet = true
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
                                .collectAsStateWithLifecycle(WinKind.COIN.enumValue)

                            Column {
                                RiveUI(
                                    file = riveFile,
                                    viewModelInstance = vmi,
                                    fit = Fit.LAYOUT,
                                    modifier = Modifier
                                        .semantics {
                                            contentDescription = "Rive UI - Rewards Demo"
                                        }
                                )

                                val sheetState = rememberModalBottomSheetState()
                                if (showBottomSheet) {
                                    ModalBottomSheet(
                                        onDismissRequest = {
                                            showBottomSheet = false
                                        },
                                        sheetState = sheetState
                                    ) {
                                        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeGestures)) {
                                            val labelWidth: Dp = 50.dp

                                            FormLabel("Lives")
                                            LabeledValueSlider(
                                                value = lives,
                                                onValueChange = { newValue ->
                                                    vmi.setNumber("Energy_Bar/Lives", newValue)
                                                },
                                                max = 5f,
                                                steps = 4,
                                                labelWidth = labelWidth
                                            )
                                            FormLabel("Energy")
                                            LabeledValueSlider(
                                                value = energy,
                                                onValueChange = { newValue ->
                                                    vmi.setNumber("Energy_Bar/Energy_Bar", newValue)
                                                },
                                                max = 100f,
                                                labelWidth = labelWidth
                                            )
                                            FormLabel("Energy Bar Color")
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp)
                                            ) {
                                                val setColor = { color: Color ->
                                                    vmi.setColor(
                                                        "Energy_Bar/Bar_Color",
                                                        color.toArgb()
                                                    )
                                                }
                                                ColorSwatch(
                                                    color = Color.Red,
                                                    setColor = setColor
                                                )
                                                ColorSwatch(
                                                    color = Color.Green,
                                                    setColor = setColor
                                                )
                                                ColorSwatch(
                                                    color = Color.Blue,
                                                    setColor = setColor
                                                )
                                                ColorSwatch(
                                                    color = Color.Transparent,
                                                    setColor = setColor
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Clear color",
                                                        tint = Color.DarkGray
                                                    )
                                                }
                                            }

                                            FormLabel("Coins")
                                            LabeledValueSlider(
                                                value = coins,
                                                onValueChange = { newValue ->
                                                    vmi.setNumber("Coin/Item_Value", newValue)
                                                },
                                                max = 10000f,
                                                steps = 19,
                                                valueFormatter = { v ->
                                                    String.format(Locale.US, "%,d", v.toInt())
                                                },
                                                labelWidth = labelWidth
                                            )
                                            FormLabel("Gems")
                                            LabeledValueSlider(
                                                value = gems,
                                                onValueChange = { newValue ->
                                                    vmi.setNumber("Gem/Item_Value", newValue)
                                                },
                                                max = 5000f,
                                                steps = 19,
                                                valueFormatter = { v ->
                                                    String.format(Locale.US, "%,d", v.toInt())
                                                },
                                                labelWidth = labelWidth
                                            )

                                            FormLabel("Win Value")
                                            LabeledValueSlider(
                                                value = winValue,
                                                onValueChange = { newValue ->
                                                    vmi.setNumber("Price_Value", newValue)
                                                },
                                                max = 100f,
                                                steps = 19,
                                                labelWidth = labelWidth
                                            )
                                            FormLabel("Win Kind")
                                            val selectedKind =
                                                WinKind.valueOf(winKind.uppercase(locale = Locale.US))
                                            Row {
                                                WinKind.entries.forEach { kind ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(end = 16.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = (kind == selectedKind),
                                                            onClick = {
                                                                vmi.setEnum(
                                                                    "Item_Selection/Item_Selection",
                                                                    kind.enumValue
                                                                )
                                                            })
                                                        Text(
                                                            text = kind.label(),
                                                            modifier = Modifier.padding(start = 4.dp)
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
                }
            }
        }
    }
}

/** Small helper that styles field/section labels using the app theme. */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

/** Reusable slider that shows the current value on the left and a Slider on the right. */
@Composable
fun LabeledValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    max: Float,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    labelWidth: Dp? = null,
) {
    val valueText = valueFormatter(value.coerceIn(0f, max))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (labelWidth != null) {
            Box(modifier = Modifier.width(labelWidth)) {
                Text(valueText)
            }
        }
        Slider(
            value = value.coerceIn(0f, max),
            onValueChange = { onValueChange(it.coerceIn(0f, max)) },
            valueRange = 0f..max,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Clickable color swatch that calls back when selected. */
@Composable
fun ColorSwatch(
    color: Color,
    setColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                Color.LightGray,
                RoundedCornerShape(10.dp)
            )
            .size(48.dp)
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable {
                setColor(color)
            },
        contentAlignment = Alignment.Center
    ) {
        children()
    }
}
