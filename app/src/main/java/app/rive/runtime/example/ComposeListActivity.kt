package app.rive.runtime.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

class ComposeListActivity : ComponentActivity() {
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
                RiveFileSource.RawRes.from(R.raw.lists_demo),
                riveWorker
            )

            val maxListItems = 10

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    when (riveFile) {
                        is Result.Loading -> LoadingIndicator()
                        is Result.Error -> ErrorMessage(riveFile.throwable)
                        is Result.Success -> {
                            val mainVMI = rememberViewModelInstance(
                                riveFile.value,
                                ViewModelSource.Named("main").blankInstance()
                            )
                            val scope = rememberCoroutineScope()

                            // Store instances of the VMIs from the Rive file
                            val itemVMIs = remember(riveFile.value) {
                                listOf("sw", "st", "bs", "br")
                            }.map { instanceName ->
                                rememberViewModelInstance(
                                    riveFile.value,
                                    ViewModelSource.Named("listItem").namedInstance(instanceName)
                                )
                            }
                            // Create a fifth, custom item VMI
                            val customItemVMI = rememberViewModelInstance(
                                riveFile.value,
                                ViewModelSource.Named("listItem").blankInstance()
                            )
                            val allVMIs = itemVMIs + customItemVMI

                            var menuSize by remember { mutableIntStateOf(0) }

                            /**
                             * Helper to keep the list length up to date after mutating operations.
                             */
                            suspend fun mutateMenuAndSync(block: suspend () -> Unit) {
                                try {
                                    block()
                                } finally {
                                    menuSize = mainVMI.getListSize("menu")
                                }
                            }

                            val canAdd = menuSize < maxListItems
                            val canRemove = menuSize > 0
                            val canSwap = menuSize > 1

                            /**
                             * Create a clone of the given VMI that has independent state.
                             *
                             * ⚠️ Because this uses [ViewModelInstance.fromFile] and not
                             * [rememberViewModelInstance] (because it runs outside of composition),
                             * the returned VMI must be closed or used with a `use` block.
                             */
                            suspend fun cloneListItem(item: ViewModelInstance): ViewModelInstance {
                                val clone = ViewModelInstance.fromFile(
                                    riveFile.value,
                                    ViewModelSource
                                        .Named("listItem")
                                        .blankInstance()
                                )
                                clone.setString(
                                    "label",
                                    item.getStringFlow("label").first()
                                )
                                clone.setString(
                                    "fontIcon",
                                    item.getStringFlow("fontIcon").first()
                                )
                                clone.setColor(
                                    "hoverColor",
                                    item.getColorFlow("hoverColor").first()
                                )

                                return clone
                            }

                            /** Initial list setup. */
                            LaunchedEffect(mainVMI, itemVMIs, customItemVMI) {
                                mainVMI.setBoolean("menuOpen", true)

                                // Configure the custom list item
                                customItemVMI.setString("label", "Between the Stars")
                                customItemVMI.setString("fontIcon", "\uF4FB") // Astronaut icon
                                customItemVMI.setColor("hoverColor", Color(0xFFF5CA53).toArgb())

                                // Append clones of all items
                                mutateMenuAndSync {
                                    allVMIs.forEach { vmi ->
                                        cloneListItem(vmi).use { clone ->
                                            mainVMI.appendToList("menu", clone)
                                        }
                                    }
                                }
                            }

                            Rive(
                                riveFile.value,
                                modifier = Modifier.weight(1f),
                                viewModelInstance = mainVMI,
                                fit = Fit.Layout(3f),
                            )

                            Column(
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal)
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    Arrangement.SpaceAround,
                                    Alignment.CenterVertically
                                ) {
                                    Button(
                                        {
                                            scope.launch {
                                                if (!canAdd) return@launch
                                                val randomIndex = (0 until allVMIs.size).random()
                                                cloneListItem(allVMIs[randomIndex]).use { clone ->
                                                    mutateMenuAndSync {
                                                        mainVMI.insertToListAtIndex(
                                                            "menu",
                                                            0,
                                                            clone
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canAdd
                                    ) {
                                        Text("Insert Front")
                                    }
                                    Button(
                                        {
                                            scope.launch {
                                                val listSize = mainVMI.getListSize("menu")
                                                if (!canAdd) return@launch
                                                val randomIndex =
                                                    (0 until allVMIs.size).random()
                                                cloneListItem(allVMIs[randomIndex]).use { clone ->
                                                    mutateMenuAndSync {
                                                        mainVMI.insertToListAtIndex(
                                                            "menu",
                                                            listSize,
                                                            clone
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canAdd
                                    ) {
                                        Text("Insert Back")
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    Arrangement.SpaceAround,
                                    Alignment.CenterVertically
                                ) {
                                    Button(
                                        {
                                            scope.launch {
                                                if (!canRemove) return@launch
                                                mutateMenuAndSync {
                                                    mainVMI.removeFromListAtIndex("menu", 0)
                                                }
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canRemove
                                    ) {
                                        Text("Remove First")
                                    }
                                    Button(
                                        {
                                            scope.launch {
                                                val listSize = mainVMI.getListSize("menu")
                                                if (!canRemove) return@launch
                                                mutateMenuAndSync {
                                                    mainVMI.removeFromListAtIndex(
                                                        "menu",
                                                        listSize - 1
                                                    )
                                                }
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canRemove
                                    ) {
                                        Text("Remove Last")
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    Arrangement.SpaceAround,
                                    Alignment.CenterVertically
                                ) {
                                    Button(
                                        {
                                            scope.launch {
                                                val listSize = mainVMI.getListSize("menu")
                                                if (!canSwap) return@launch
                                                val index1 = (0 until listSize).random()
                                                val index2 =
                                                    (0 until listSize).filter { it != index1 }
                                                        .random()
                                                mainVMI.swapListItems("menu", index1, index2)
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canSwap
                                    ) {
                                        Text("Swap Two")
                                    }
                                    // Demonstrates retrieving an item from the list
                                    val context = LocalContext.current
                                    Button(
                                        {
                                            scope.launch {
                                                val listSize = mainVMI.getListSize("menu")
                                                if (!canAdd) return@launch
                                                val randomIndex = (0 until listSize).random()
                                                ViewModelInstance.fromFile(
                                                    riveFile.value,
                                                    ViewModelInstanceSource.ReferenceListItem(
                                                        mainVMI,
                                                        "menu",
                                                        randomIndex
                                                    )
                                                ).use { item ->
                                                    val itemLabel =
                                                        item.getStringFlow("label").first()
                                                    val suffix = when (randomIndex) {
                                                        0 -> "st"
                                                        1 -> "nd"
                                                        2 -> "rd"
                                                        else -> "th"
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        "${randomIndex + 1}$suffix item: $itemLabel",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        Modifier.width(150.dp),
                                        enabled = canAdd
                                    ) {
                                        Text("Toast Random")
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
