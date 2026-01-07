package app.rive.runtime.example.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rive.runtime.example.ComposeDataBindingActivity
import app.rive.runtime.example.LegacyDataBindingActivity
import app.rive.runtime.example.WinKind
import java.util.Locale

/**
 * A shared bottom sheet for options to configure both [ComposeDataBindingActivity] and
 * [LegacyDataBindingActivity].
 *
 * @param lives The current number of lives.
 * @param energy The current energy level.
 * @param coins The current number of coins.
 * @param gems The current number of gems.
 * @param winValue The current value earned for winning.
 * @param winKind The current kind of currency earned for winning.
 * @param onDismiss A callback for when the bottom sheet is dismissed.
 * @param onVmiSetNumber A callback for when a number property is set.
 * @param onVmiSetColor A callback for when a color property is set.
 * @param onVmiSetEnum A callback for when an enum property is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsBottomPanel(
    lives: Float,
    energy: Float,
    coins: Float,
    gems: Float,
    winValue: Float,
    winKind: WinKind,
    onDismiss: () -> Unit,
    onVmiSetNumber: (String, Float) -> Unit,
    onVmiSetColor: (String, Int) -> Unit,
    onVmiSetEnum: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeGestures)) {
            val labelWidth: Dp = 50.dp

            LabelledComponent("Lives") {
                ValueSlider(
                    value = lives,
                    onValueChange = { newValue -> onVmiSetNumber("Energy_Bar/Lives", newValue) },
                    max = 5f,
                    steps = 4,
                    labelWidth = labelWidth
                )
            }

            LabelledComponent("Energy") {
                ValueSlider(
                    value = energy,
                    onValueChange = { newValue ->
                        onVmiSetNumber("Energy_Bar/Energy_Bar", newValue)
                    },
                    max = 100f,
                    labelWidth = labelWidth
                )
            }

            LabelledComponent("Energy Bar Color") {
                ColorSwatchRow(
                    colors = listOf(
                        Color.Red to {},
                        Color.Green to {},
                        Color.Blue to {},
                        Color.Transparent to {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear color",
                                tint = Color.DarkGray
                            )
                        }
                    ),
                    { color: Color ->
                        onVmiSetColor("Energy_Bar/Bar_Color", color.toArgb())
                    })
            }

            LabelledComponent("Coins") {
                ValueSlider(
                    value = coins,
                    onValueChange = { newValue -> onVmiSetNumber("Coin/Item_Value", newValue) },
                    max = 10000f,
                    steps = 19,
                    valueFormatter = { v ->
                        String.format(Locale.US, "%,d", v.toInt())
                    },
                    labelWidth = labelWidth
                )
            }

            LabelledComponent("Gems") {
                ValueSlider(
                    value = gems,
                    onValueChange = { newValue -> onVmiSetNumber("Gem/Item_Value", newValue) },
                    max = 5000f,
                    steps = 19,
                    valueFormatter = { v ->
                        String.format(Locale.US, "%,d", v.toInt())
                    },
                    labelWidth = labelWidth
                )
            }

            LabelledComponent("Win Value") {
                ValueSlider(
                    value = winValue,
                    onValueChange = { newValue -> onVmiSetNumber("Price_Value", newValue) },
                    max = 100f,
                    steps = 19,
                    labelWidth = labelWidth
                )
            }
            LabelledComponent("Win Kind") {
                RadioGroup(
                    WinKind.entries.map { it.enumValue to it.label() },
                    winKind.enumValue
                ) { selected -> onVmiSetEnum("Item_Selection/Item_Selection", selected) }
            }
        }
    }
}
