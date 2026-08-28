@file:OptIn(app.rive.ExperimentalRiveSemantics::class)

package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveSemanticsMode
import app.rive.ViewModelSource
import app.rive.rememberArtboard
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import android.graphics.Color as AndroidColor

class SemanticsTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val riveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.semantic_test),
                riveWorker
            )

            Scaffold(containerColor = Color.Black) { innerPadding ->
                when (riveFile) {
                    is Result.Loading -> LoadingIndicator()
                    is Result.Error -> ErrorMessage(riveFile.throwable)
                    is Result.Success -> {
                        val file = riveFile.value
                        val artboardNames by produceState(emptyList<String>(), file) {
                            value = file.getArtboardNames()
                        }

                        var selectedArtboardName by remember(file) { mutableStateOf<String?>(null) }
                        LaunchedEffect(artboardNames) {
                            if (selectedArtboardName == null && artboardNames.isNotEmpty()) {
                                selectedArtboardName = artboardNames.first()
                            }
                        }

                        val selectedArtboard = rememberArtboard(file, selectedArtboardName)
                        val viewModelInstance = rememberViewModelInstance(
                            file,
                            ViewModelSource.DefaultForArtboard(selectedArtboard).defaultInstance()
                        )

                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .background(Color.DarkGray),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val density = LocalDensity.current
                            with(density) {
                                Box(
                                    Modifier
                                        .weight(1f, fill = true)
                                        .align(Alignment.CenterHorizontally)
                                        .background(Color.Red)
                                ) {

                                    Rive(
                                        file = file,
                                        artboard = selectedArtboard,
                                        viewModelInstance = viewModelInstance,
                                        fit = Fit.Contain(),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(Color.Blue),
                                        semantics = RiveSemanticsMode.Automatic
                                    )
                                }
                            }

                            if (artboardNames.isEmpty()) {
                                Text(
                                    text = "No artboards found in this file.",
                                    color = Color.White,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                LabelledDropdown(
                                    label = "Artboard",
                                    options = artboardNames,
                                    selectedOption = selectedArtboardName ?: artboardNames.first(),
                                    onOptionSelected = { selected ->
                                        selectedArtboardName = selected
                                    }
                                )
                            }

                            val dpi = LocalConfiguration.current.densityDpi
                            Text(
                                text = "Density: ${"%.2f".format(density.density)}x, DPI: $dpi",
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
