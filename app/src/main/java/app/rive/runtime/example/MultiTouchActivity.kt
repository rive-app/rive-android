package app.rive.runtime.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.launch
import java.util.Locale

class MultiTouchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MultiTouchScreen() }
    }
}

/** Named holder for a tracked pointer slot. */
private data class Slot(val pointerId: Int = -1, val x: Float? = null, val y: Float? = null)

@Composable
@SuppressLint("ClickableViewAccessibility")
private fun MultiTouchScreen() {
    val context = LocalContext.current
    val locale = remember { Locale.getDefault() }

    // Each slot holds: pointerId (‑1 if free), x, y
    val slots = remember { List(5) { Slot() }.toMutableStateList() }

    val riveTouches = remember { List(5) { false }.toMutableStateList() }

    // Multitouch toggle state (default to enabled for this sample)
    var multiTouchEnabled by rememberSaveable { mutableStateOf(true) }

    fun findSlotForPointerId(pointerId: Int): Int = slots.indexOfFirst { it.pointerId == pointerId }
    fun firstFreeSlot(): Int = slots.indexOfFirst { it.pointerId == -1 }
    fun setSlot(slot: Int, pointerId: Int, x: Float?, y: Float?) {
        require(slot in 0..4)
        slots[slot] = Slot(pointerId, x, y)
    }

    fun clearSlot(slot: Int) = setSlot(slot, -1, null, null)
    fun clearAllSlots() = (0..4).forEach(::clearSlot)

    fun labelFor(slot: Int, s: Slot): String {
        val x = s.x
        val y = s.y
        return if (x == null || y == null) {
            "Finger $slot: —"
        } else {
            "Finger $slot: (" +
                    String.format(locale, "X: %.1f", x) + ", " +
                    String.format(locale, "Y: %.1f", y) + ", " +
                    "ID: ${s.pointerId})"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Rive view
        AndroidView(
            factory = {
                RiveAnimationView.Builder(context)
                    .setResource(R.raw.multitouch)
                    .setAutoBind(true)
                    .setMultiTouchEnabled(multiTouchEnabled)
                    .build().apply {
                        doOnAttach {
                            val lifecycleOwner = findViewTreeLifecycleOwner() ?: return@doOnAttach
                            val vmi = controller.stateMachines.first().viewModelInstance!!

                            val collectJob = lifecycleOwner.lifecycleScope.launch {
                                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    // Collect 5 boolean properties: "Target 1/Down" ... "Target 5/Down"
                                    (1..5).forEach { n ->
                                        val flow =
                                            vmi.getBooleanProperty("Target $n/Down").valueFlow
                                        launch {
                                            flow.collect { isDown ->
                                                riveTouches[n - 1] = isDown
                                            }
                                        }
                                    }
                                }
                            }
                            doOnDetach { collectJob.cancel() }
                        }

                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                    val i = event.actionIndex
                                    if (!multiTouchEnabled && i != 0) return@setOnTouchListener false
                                    val id = event.getPointerId(i)
                                    var slot = findSlotForPointerId(id)
                                    if (slot == -1) slot = firstFreeSlot()
                                    if (slot != -1) {
                                        setSlot(slot, id, event.getX(i), event.getY(i))
                                    }
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    val range =
                                        if (multiTouchEnabled) 0 until event.pointerCount else 0 until minOf(
                                            1,
                                            event.pointerCount
                                        )
                                    for (i in range) {
                                        val id = event.getPointerId(i)
                                        val slot = findSlotForPointerId(id)
                                        if (slot != -1) {
                                            setSlot(slot, id, event.getX(i), event.getY(i))
                                        }
                                    }
                                }

                                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                    val i = event.actionIndex
                                    if (!multiTouchEnabled && i != 0) return@setOnTouchListener false
                                    val id = event.getPointerId(i)
                                    val slot = findSlotForPointerId(id)
                                    if (slot != -1) clearSlot(slot)
                                }

                                MotionEvent.ACTION_CANCEL -> clearAllSlots()
                            }
                            false
                        }
                    }
            },
            update = { view ->
                // Reflect switch changes into the Rive view without recreating it
                view.multiTouchEnabled = multiTouchEnabled
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Compose rendered boxes that reflect Rive data binding touch outputs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(top = 8.dp)
        ) {
            repeat(5) { idx ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(if (riveTouches[idx]) Color(0xFF17A410) else Color(0xFFA41010))
                )
            }
        }

        // Finger labels
        slots.forEachIndexed { idx, slot ->
            Text(
                text = labelFor(idx, slot),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }

        // Toggle row for enabling/disabling multitouch handling inside RiveAnimationView
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (multiTouchEnabled) "Multitouch: On" else "Multitouch: Off",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Switch(
                checked = multiTouchEnabled,
                onCheckedChange = {
                    multiTouchEnabled = it
                    if (!it) clearAllSlots()
                }
            )
        }
    }
}
