package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveUI
import app.rive.ViewModelSource
import app.rive.rememberArtboard
import app.rive.rememberCommandQueueOrNull
import app.rive.rememberRegisteredFont
import app.rive.rememberRiveFile
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.Fit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import app.rive.runtime.kotlin.core.Alignment as RiveAlignment

class ComposeActivity : ComponentActivity() {
    @OptIn(ExperimentalRiveComposeAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable Logcat logging
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val context = LocalContext.current

            /**
             * Create a command queue (worker thread) to run Rive. This uses the safe version,
             * `rememberCommandQueueOrNull`, which returns null if it fails to create the command
             * queue, allowing us to handle the error gracefully. Ideally it should never fail, but
             * this is a safeguard for production deployments.
             */
            val errorState = remember { mutableStateOf<Throwable?>(null) }
            val cq = rememberCommandQueueOrNull(errorState)
            if (cq == null) {
                // If the command queue could not be created, show an error
                ErrorMessage(errorState.value!!)
                return@setContent
            }
            val commandQueue = cq

            // Load the Inter font from raw resources
            val fontBytes by produceState<ByteArray?>(null) {
                value = withContext(Dispatchers.IO) {
                    context.resources.openRawResource(R.raw.inter).use { it.readBytes() }
                }
            }

            // Decode and register the font with the command queue (available to all Rive files on this queue)
            val font = when (val bytes = fontBytes) {
                null -> Result.Loading
                // Only register after the bytes are loaded
                // The key, "Inter-594377", is the name in the zip produced by exporting from Rive
                else -> rememberRegisteredFont(commandQueue, "Inter-594377", bytes).value
            }

            // Point to the Rive raw resource file
            var fileSource by remember { mutableStateOf(RiveFileSource.RawRes(R.raw.rating_animation_all)) }

            // Gate file loading on the font being ready, otherwise propagate the loading or error state
            val riveFile = when (font) {
                is Result.Loading -> Result.Loading
                is Result.Error -> Result.Error(font.throwable)
                is Result.Success -> rememberRiveFile(fileSource, commandQueue).value
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    // Switch on the status of the loading Rive file
                    when (val file = riveFile) {
                        is Result.Loading -> LoadingIndicator()
                        is Result.Error -> ErrorMessage(file.throwable)
                        is Result.Success -> {
                            // On success, we can use the Rive file
                            val riveFile = file.value

                            // Query the Rive file for artboard names
                            val artboardNames by produceState<List<String>>(emptyList(), riveFile) {
                                value = riveFile.getArtboardNames()
                            }

                            var artboardName by rememberSaveable { mutableStateOf<String?>(null) }
                            // Load the artboard by name, or the default if null
                            val artboard = rememberArtboard(riveFile, artboardName)

                            // Store fit and alignment as strings, map to Rive types
                            var fitString by rememberSaveable { mutableStateOf("Contain") }
                            val fit = fitMap[fitString] ?: Fit.CONTAIN
                            var alignmentString by rememberSaveable { mutableStateOf("Center") }
                            val alignment = alignmentMap[alignmentString] ?: RiveAlignment.CENTER

                            // Create a view model instance that can be used by all 3 artboards
                            val vmi = rememberViewModelInstance(
                                riveFile,
                                ViewModelSource.Named("Rating Animation").defaultInstance()
                            )
                            // Collect the rating value by the name of the property
                            val rating by vmi.getNumberFlow("Number_Star", 0f).collectAsState()

                            Column {
                                // Render the Rive UI
                                RiveUI(
                                    file = riveFile,
                                    artboard = artboard,
                                    viewModelInstance = vmi,
                                    fit = fit,
                                    alignment = alignment,
                                    modifier = Modifier
                                        .height(300.dp)
                                        .semantics {
                                            contentDescription = "Rive UI: $artboardName"
                                        }
                                )

                                // Reactive slider - changes and is changed by Rive
                                RatingSlider(
                                    sliderValue = rating,
                                    onValueChange = {
                                        vmi.setNumber("Number_Star", it)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Artboard, fit, and alignment selectors
                                LabelledDropdown(
                                    label = "Artboard",
                                    options = artboardNames,
                                    selectedOption = artboardName ?: "Default",
                                    onOptionSelected = { selectedArtboard ->
                                        artboardName = selectedArtboard
                                    })

                                LabelledDropdown(
                                    label = "Fit",
                                    options = fitMap.keys.toList(),
                                    selectedOption = fitString,
                                    onOptionSelected = { selectedFit ->
                                        fitString = selectedFit
                                    })

                                LabelledDropdown(
                                    label = "Alignment",
                                    options = alignmentMap.keys.toList(),
                                    selectedOption = alignmentString,
                                    onOptionSelected = { selectedAlignment ->
                                        alignmentString = selectedAlignment
                                    })
                            }
                        }
                    }
                }
            }
        }
    }

    // Map of Fit and Alignment options for presenting to the user
    private val fitMap = mapOf<String, Fit>(
        "Contain" to Fit.CONTAIN,
        "Cover" to Fit.COVER,
        "Fill" to Fit.FILL,
        "Fit Width" to Fit.FIT_WIDTH,
        "Fit Height" to Fit.FIT_HEIGHT,
        "Layout" to Fit.LAYOUT,
        "Scale Down" to Fit.SCALE_DOWN,
        "None" to Fit.NONE
    )

    private val alignmentMap = mapOf<String, RiveAlignment>(
        "Top Left" to RiveAlignment.TOP_LEFT,
        "Top Center" to RiveAlignment.TOP_CENTER,
        "Top Right" to RiveAlignment.TOP_RIGHT,
        "Center Left" to RiveAlignment.CENTER_LEFT,
        "Center" to RiveAlignment.CENTER,
        "Center Right" to RiveAlignment.CENTER_RIGHT,
        "Bottom Left" to RiveAlignment.BOTTOM_LEFT,
        "Bottom Center" to RiveAlignment.BOTTOM_CENTER,
        "Bottom Right" to RiveAlignment.BOTTOM_RIGHT
    )
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading Rive content" },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Loading",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorMessage(
    throwable: Throwable,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${throwable.message}",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RatingSlider(
    modifier: Modifier = Modifier,
    sliderValue: Float = 0f,
    onValueChange: (Float) -> Unit = {},
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = CenterHorizontally
    ) {
        Text(text = "Rating: ${sliderValue.toInt()}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = sliderValue,
            onValueChange = {
                // Values may be close but not exact, e.g. 2.998f, so we round
                onValueChange(it.roundToInt().toFloat())
            },
            valueRange = 0f..5f,
            steps = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LabelledDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp)
        )
        Box(
            modifier = Modifier.wrapContentSize(Alignment.TopStart)
        ) {
            Button(onClick = { expanded = true }) {
                Row {
                    Text(
                        text = selectedOption,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dropdown"
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onOptionSelected(option)
                        }
                    )
                }
            }
        }
    }
}
