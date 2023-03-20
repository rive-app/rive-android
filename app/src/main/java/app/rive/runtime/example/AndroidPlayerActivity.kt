package app.rive.runtime.example

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveArtboardRenderer.Listener
import app.rive.runtime.kotlin.core.*

class AndroidPlayerActivity : AppCompatActivity() {
    var loop: Loop = Loop.AUTO
    var direction: Direction = Direction.AUTO
    var playButtonMap: HashMap<String, View> = HashMap()
    var pauseButtonMap: HashMap<String, View> = HashMap()
    var stopButtonMap: HashMap<String, View> = HashMap()

    private val animationResources = listOf(
        R.raw.artboard_animations,
        R.raw.basketball,
        R.raw.circle_move,
        R.raw.clipping,
        R.raw.explorer,
        R.raw.f22,
        R.raw.flux_capacitor,
        R.raw.loopy,
        R.raw.mascot,
        R.raw.neostream,
        R.raw.off_road_car_blog,
        R.raw.progress,
        R.raw.pull,
        R.raw.rope,
        R.raw.skills,
        R.raw.trailblaze,
        R.raw.ui_swipe_left_to_delete,
        R.raw.vader,
        R.raw.wacky,
        R.raw.what_a_state,
        R.raw.two_bone_ik,
        R.raw.constrained,
    )

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.android_player_view)
    }

    val resourceNames: List<String>
        get() {
            return animationResources.map { resources.getResourceName(it).split('/').last() }
        }

    fun loadResource(index: Int) {
        animationView.setRiveResource(animationResources[index], artboardName = null)
        setSpinner()

        playButtonMap.clear()
        pauseButtonMap.clear()
        stopButtonMap.clear()

        animationView.renderer.file?.firstArtboard?.name?.let {
            loadArtboard(it)
        }
    }


    fun onLoopModeSelected(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.getId()) {
                R.id.loop_auto ->
                    loop = Loop.AUTO
                R.id.loop_loop ->
                    loop = Loop.LOOP
                R.id.loop_oneshot ->
                    loop = Loop.ONESHOT
                R.id.loop_pingpong ->
                    loop = Loop.PINGPONG
            }
        }
    }

    fun onDirectionSelected(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.getId()) {
                R.id.direction_auto ->
                    direction = Direction.AUTO
                R.id.direction_backwards ->
                    direction = Direction.BACKWARDS
                R.id.direction_forwards ->
                    direction = Direction.FORWARDS
            }
        }
    }

    fun onReset(view: View) {
        if (view is AppCompatButton) {
            animationView.reset()
        }
    }

    fun addAnimationControl(animationName: String): View {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.END

        val text = TextView(this)
        text.text = animationName

        val playButton = AppCompatButton(this)
        playButton.text = ">"
        playButton.background.setTint(Color.WHITE)
        playButton.setOnClickListener {
            animationView.play(animationName, loop, direction)
        }
        playButtonMap[animationName] = playButton

        val pauseButton = AppCompatButton(this)
        pauseButton.text = "||"
        pauseButton.background.setTint(Color.WHITE)
        pauseButton.setOnClickListener {
            animationView.pause(animationName)
        }
        pauseButtonMap[animationName] = pauseButton

        val stopButton = AppCompatButton(this)
        stopButton.text = "[]"
        stopButton.background.setTint(Color.RED)
        stopButton.setOnClickListener {
            animationView.stop(animationName)
        }
        stopButtonMap[animationName] = stopButton


        layout.addView(text)
        layout.addView(playButton)
        layout.addView(pauseButton)
        layout.addView(stopButton)
        return layout
    }


    fun addStateMachineControl(artboard: Artboard, stateMachineName: String): List<View> {
        val views = mutableListOf<View>()
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.END

        val text = TextView(this)
        text.text = stateMachineName

        val playButton = AppCompatButton(this)
        playButton.text = ">"
        playButton.background.setTint(Color.WHITE)
        playButton.setOnClickListener {
            animationView.play(stateMachineName, loop, direction, isStateMachine = true)
        }
        playButtonMap[stateMachineName] = playButton

        val pauseButton = AppCompatButton(this)
        pauseButton.text = "||"
        pauseButton.background.setTint(Color.WHITE)
        pauseButton.setOnClickListener {
            animationView.pause(stateMachineName, isStateMachine = true)
        }
        pauseButtonMap[stateMachineName] = pauseButton

        val stopButton = AppCompatButton(this)
        stopButton.text = "[]"
        stopButton.background.setTint(Color.RED)
        stopButton.setOnClickListener {
            animationView.stop(stateMachineName, isStateMachine = true)
        }
        stopButtonMap[stateMachineName] = stopButton

        val stateMachine = artboard.stateMachine(stateMachineName)

        layout.addView(text)
        layout.addView(playButton)
        layout.addView(pauseButton)
        layout.addView(stopButton)
        views.add(layout)


        stateMachine.inputs.forEach {
            val innerLayout = LinearLayout(this)
            innerLayout.orientation = LinearLayout.HORIZONTAL
            innerLayout.gravity = Gravity.END

            val innerText = TextView(this)
            innerText.text = it.name
            innerLayout.addView(innerText)

            if (it.isTrigger) {
                val triggerButton = AppCompatButton(this)
                triggerButton.text = "Fire"
                triggerButton.background.setTint(Color.WHITE)
                triggerButton.setOnClickListener { _ ->
                    animationView.fireState(stateMachineName, it.name)
                }
                innerLayout.addView(triggerButton)
            }

            if (it.isBoolean) {
                val boolBox = AppCompatCheckBox(this)
                if ((it as SMIBoolean).value) {
                    boolBox.isChecked = true
                }
                boolBox.setOnCheckedChangeListener { _, b ->
                    animationView.setBooleanState(stateMachineName, it.name, b)
                }
                innerLayout.addView(boolBox)
            }

            if (it.isNumber) {
                val editText = AppCompatEditText(this)
                editText.setText((it as SMINumber).value.toString())
                val editTriggerButton = AppCompatButton(this)
                editTriggerButton.text = "Apply"
                editTriggerButton.background.setTint(Color.WHITE)
                editTriggerButton.setOnClickListener { _ ->
                    try {
                        val value = editText.text.toString().toFloat()
                        animationView.setNumberState(stateMachineName, it.name, value)
                    } catch (e: Error) {

                    }
                }

                innerLayout.addView(editText)
                innerLayout.addView(editTriggerButton)
            }

            views.add(innerLayout)
        }

        return views
    }

    fun loadArtboard(artboardName: String) {
        val controls = findViewById<LinearLayout>(R.id.controls)
        controls.removeAllViews()
        animationView.renderer.file?.artboard(artboardName)?.let { artboard ->
            if (artboard.stateMachineNames.size > 0) {
                val stateMachineHeader = TextView(this)
                stateMachineHeader.text = "State Machines:"
                controls.addView(stateMachineHeader)
                artboard.stateMachineNames.forEach {
                    addStateMachineControl(artboard, it).forEach {
                        controls.addView(it)
                    }
                }
            }
            if (artboard.animationNames.size > 0) {
                val animationsHeader = TextView(this)
                animationsHeader.text = "Animations:"
                controls.addView(animationsHeader)
                artboard.animationNames.forEach {
                    controls.addView(addAnimationControl(it))
                }
            }
        }

    }

    fun setSpinner() {
        animationView.renderer.file?.artboardNames?.let { artboardNames ->
            val dropdown = findViewById<Spinner>(R.id.artboards)
            val adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                artboardNames
            )
            dropdown.adapter = adapter
            dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    arg0: AdapterView<*>?,
                    arg1: View?,
                    arg2: Int,
                    arg3: Long
                ) {
                    val item = dropdown.selectedItem.toString()

                    animationView.artboardName = item
                    loadArtboard(item)
                }

                override fun onNothingSelected(arg0: AdapterView<*>?) {}
            }
        }
    }


    fun setResourceSpinner() {
        animationResources.let { _ ->
            val dropdown = findViewById<Spinner>(R.id.resources)
            val adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                resourceNames
            )
            dropdown.adapter = adapter
            dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    arg0: AdapterView<*>?,
                    arg1: View?,
                    arg2: Int,
                    arg3: Long
                ) {
                    loadResource(arg2)
                }

                override fun onNothingSelected(arg0: AdapterView<*>?) {}
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.android_player)
        setResourceSpinner()
        loadResource(0)
        val that = this
        val events = findViewById<LinearLayout>(R.id.events)
        val listener = object : Listener {
            override fun notifyPlay(animation: PlayableInstance) {
                var text: String? = null
                if (animation is LinearAnimationInstance) {
                    text = animation.name
                } else if (animation is StateMachineInstance) {
                    text = animation.name
                }
                text?.let { theText ->
                    runOnUiThread {
                        val textView = TextView(that)
                        textView.text = "Play $theText"
                        events.addView(textView, 0)
                        playButtonMap[theText]?.background?.setTint(Color.GREEN)
                        pauseButtonMap[theText]?.background?.setTint(Color.WHITE)
                        stopButtonMap[theText]?.background?.setTint(Color.WHITE)
                    }
                }
            }

            override fun notifyPause(animation: PlayableInstance) {
                var text: String? = null
                if (animation is LinearAnimationInstance) {
                    text = animation.name
                } else if (animation is StateMachineInstance) {
                    text = animation.name
                }
                text?.let {
                    runOnUiThread {
                        val textView = TextView(that)
                        textView.text = "Pause $text"
                        events.addView(textView, 0)
                        playButtonMap[text]?.background?.setTint(Color.WHITE)
                        pauseButtonMap[text]?.background?.setTint(Color.BLUE)
                        stopButtonMap[text]?.background?.setTint(Color.WHITE)
                    }
                }
            }

            override fun notifyStop(animation: PlayableInstance) {
                var text: String? = null
                if (animation is LinearAnimationInstance) {
                    text = animation.name
                } else if (animation is StateMachineInstance) {
                    text = animation.name
                }
                text?.let {
                    runOnUiThread {
                        val textView = TextView(that)
                        textView.text = "Stop $text"
                        events.addView(textView, 0)
                        playButtonMap[text]?.background?.setTint(Color.WHITE)
                        pauseButtonMap[text]?.background?.setTint(Color.WHITE)
                        stopButtonMap[text]?.background?.setTint(Color.RED)
                    }
                }
            }

            override fun notifyLoop(animation: PlayableInstance) {
                if (animation is LinearAnimationInstance) {
                    runOnUiThread {
                        val text = TextView(that)
                        text.text = "Loop ${animation.name}"
                        events.addView(text, 0)
                    }
                }
            }

            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                runOnUiThread {
                    val text = TextView(that)
                    text.text = "$stateMachineName: State Changed: $stateName"
                    events.addView(text, 0)
                }
            }
        }

        animationView.registerListener(listener)
    }
}

