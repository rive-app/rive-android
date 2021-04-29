package app.rive.runtime.example

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveDrawable.Listener
import app.rive.runtime.kotlin.core.*

class AndroidPlayerActivity : AppCompatActivity() {
    var loop: Loop = Loop.NONE
    var direction: Direction = Direction.AUTO

    val animationResources = listOf(
        R.raw.artboard_animations,
        R.raw.basketball,
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
    )

    val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.android_player_view)
    }

    val resourceNames: List<String>
        get() {
            return animationResources.map { resources.getResourceName(it).split('/').last() }
        }

    fun loadResource(index: Int) {
        animationView.artboardName
        animationView.setRiveResource(animationResources[index], artboardName = null)
        setSpinner()
        animationView.drawable.file?.firstArtboard?.name?.let {
            loadArtboard(it)
        }
    }


    fun onLoopModeSelected(view: View) {
        if (view is RadioButton && view.isChecked) {
            // Check which radio button was clicked
            when (view.getId()) {
                R.id.loop_auto ->
                    loop = Loop.NONE
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
        var layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.END

        var text = TextView(this)
        text.setText(animationName)

        var playButton = AppCompatButton(this)
        playButton.setText(">")
        playButton.setOnClickListener {
            animationView.play(animationName, loop, direction)
        }

        var pauseButton = AppCompatButton(this)
        pauseButton.setText("||")
        pauseButton.setOnClickListener {
            animationView.pause(animationName)
        }

        var stopButton = AppCompatButton(this)
        stopButton.setText("[]")
        stopButton.setOnClickListener {
            animationView.stop(animationName)
        }


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
        text.setText(stateMachineName)

        val playButton = AppCompatButton(this)
        playButton.setText(">")
        playButton.setOnClickListener {
            animationView.play(stateMachineName, loop, direction, isStateMachine = true)
        }

        val pauseButton = AppCompatButton(this)
        pauseButton.setText("||")
        pauseButton.setOnClickListener {
            animationView.pause(stateMachineName, isStateMachine = true)
        }

        val stopButton = AppCompatButton(this)
        stopButton.setText("[]")
        stopButton.setOnClickListener {
            animationView.stop(stateMachineName, isStateMachine = true)
        }

        val stateMachine = artboard.stateMachine(stateMachineName)

        layout.addView(text)
        layout.addView(playButton)
        layout.addView(pauseButton)
        layout.addView(stopButton)
        views.add(layout)


        stateMachine.inputs.forEach {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.gravity = Gravity.END

            val text = TextView(this)
            text.setText(it.name)
            layout.addView(text)

            if (it.isTrigger) {
                val triggerButton = AppCompatButton(this)
                triggerButton.setText("x")
                triggerButton.setOnClickListener { _ ->
                    animationView.fireState(stateMachineName, it.name)
                }
                layout.addView(triggerButton)
            }

            if (it.isBoolean) {
                val boolBox = AppCompatCheckBox(this)
                if ((it as StateMachineBooleanInput).value) {
                    boolBox.isChecked = true
                }
                boolBox.setOnCheckedChangeListener { _, b ->
                    animationView.setBooleanState(stateMachineName, it.name, b)
                }
                layout.addView(boolBox)
            }

            if (it.isNumber) {
                val editText = AppCompatEditText(this)
                editText.setText((it as StateMachineNumberInput).value.toString())
                val editTriggerButton = AppCompatButton(this)
                editTriggerButton.setText("x")
                editTriggerButton.setOnClickListener { _ ->
                    try {
                        var value = editText.text.toString().toFloat()
                        animationView.setNumberState(stateMachineName, it.name, value)
                    } catch (e: Error){

                    }
                }

                layout.addView(editText)
                layout.addView(editTriggerButton)
            }

            views.add(layout)
        }

        return views
    }

    fun loadArtboard(artboardName: String) {
        var controls = findViewById<LinearLayout>(R.id.controls)
        controls.removeAllViews()
        animationView.drawable.file?.artboard(artboardName)?.let { artboard ->
            if (artboard.stateMachineNames.size > 0) {
                val stateMachineHeader = TextView(this)
                stateMachineHeader.setText("State Machines:")
                controls.addView(stateMachineHeader)
                artboard.stateMachineNames.forEach {
                    addStateMachineControl(artboard, it).forEach {
                        controls.addView(it)
                    }
                }
            }
            if (artboard.animationNames.size > 0) {
                val animationsHeader = TextView(this)
                animationsHeader.setText("Animations:")
                controls.addView(animationsHeader)
                artboard.animationNames.forEach {
                    controls.addView(addAnimationControl(it))
                }
            }
        }

    }

    fun setSpinner() {
        animationView.drawable.file?.artboardNames?.let { artboardNames ->
            var dropdown = findViewById<Spinner>(R.id.artboards)
            var adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                artboardNames
            );
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
            var dropdown = findViewById<Spinner>(R.id.resources)
            var adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                resourceNames
            );
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
                if (animation is LinearAnimationInstance) {
                    val text = TextView(that)
                    text.setText("Play ${animation.animation.name}")
                    events.addView(text, 0)
                }
            }

            override fun notifyPause(animation: PlayableInstance) {
                if (animation is LinearAnimationInstance) {
                    val text = TextView(that)
                    text.setText("Pause ${animation.animation.name}")
                    events.addView(text, 0)
                }
            }

            override fun notifyStop(animation: PlayableInstance) {
                if (animation is LinearAnimationInstance) {
                    val text = TextView(that)
                    text.setText("Stop ${animation.animation.name}")
                    events.addView(text, 0)
                }
            }

            override fun notifyLoop(animation: PlayableInstance) {
                if (animation is LinearAnimationInstance) {
                    val text = TextView(that)
                    text.setText("Loop ${animation.animation.name}")
                    events.addView(text, 0)
                }
            }
        }

        animationView.registerListener(listener)

    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}
