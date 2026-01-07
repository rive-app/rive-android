package app.rive.runtime.example.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A panel section label.
 *
 * @param text The text of the label.
 * @param modifier The modifier to apply to the label.
 */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

/**
 * A panel section with a label.
 *
 * @param label The label to display above the component.
 * @param component The component to display below the label.
 */
@Composable
fun ColumnScope.LabelledComponent(label: String, component: @Composable ColumnScope.() -> Unit) {
    FormLabel(label)
    component()
}


/**
 * Slider that shows the current value on the left and a Slider on the right.
 *
 * @param value The current value of the slider.
 * @param onValueChange A callback that is called when the value is changed.
 * @param max The maximum value of the slider.
 * @param modifier The modifier to apply to the slider.
 * @param steps The number of steps the slider can have.
 * @param valueFormatter A callback that is called to format the value before displaying it.
 * @param labelWidth The width of the label, used to space the label and slider.
 */
@Composable
fun ValueSlider(
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

/**
 * Clickable color swatch that calls back when selected.
 *
 * @param color The color of the swatch.
 * @param setColor A callback that is called when the color is selected.
 * @param modifier The modifier to apply to the swatch.
 * @param children An optional child to display within the swatch.
 */
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

/**
 * A set of selectable colors.
 *
 * @param colors A list of colors and optionally a child to display within the color swatch.
 * @param setColor A callback that is called when a color is selected.
 * @param modifier The modifier to apply to the row.
 */
@Composable
fun ColorSwatchRow(
    colors: List<Pair<Color, @Composable () -> Unit>>,
    setColor: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            ColorSwatch(
                color = color.first,
                setColor = setColor
            ) {
                color.second()
            }
        }
    }
}

/**
 * A group of radio buttons with one selected option.
 *
 * @param options The list of radio options to display, where the first element of the pair is its
 *    data binding value and the second is its label.
 * @param selectedOption The currently selected option.
 * @param onOptionSelected A callback for when an option is selected.
 */
@Composable
fun RadioGroup(
    options: List<Pair<String, String?>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row {
        options.forEach { option ->
            val value = option.first
            val label = option.second ?: value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                RadioButton(
                    selected = value == selectedOption,
                    onClick = { onOptionSelected(value) }
                )
                Text(label, Modifier.padding(start = 4.dp))
            }
        }
    }
}
