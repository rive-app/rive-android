package app.rive.runtime.example

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveDrawable.Listener
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.Rive

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
        R.raw.off_road_car_blog,
        R.raw.progress,
        R.raw.pull,
        R.raw.rope,
        R.raw.trailblaze,
        R.raw.vader,
        R.raw.wacky
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
        val that = this
        val events = findViewById<LinearLayout>(R.id.events)
        val listener = object : Listener {
            override fun notifyPlay(animation: LinearAnimationInstance) {
                val text = TextView(that)
                text.setText("Play ${animation.animation.name}")
                events.addView(text, 0)
            }

            override fun notifyPause(animation: LinearAnimationInstance) {
                val text = TextView(that)
                text.setText("Pause ${animation.animation.name}")
                events.addView(text, 0)
            }

            override fun notifyStop(animation: LinearAnimationInstance) {
                val text = TextView(that)
                text.setText("Stop ${animation.animation.name}")
                events.addView(text, 0)
            }

            override fun notifyLoop(animation: LinearAnimationInstance) {
                val text = TextView(that)
                text.setText("Loop ${animation.animation.name}")
                events.addView(text, 0)
            }
        }
        animationView.registerListener(listener)
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

    fun loadArtboard(artboardName: String) {
        var controls = findViewById<LinearLayout>(R.id.controls)
        controls.removeAllViews()
        animationView.drawable.file?.artboard(artboardName)?.animationNames?.forEach {
            controls.addView(addAnimationControl(it))
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
        animationResources.let { resourceId ->
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
        Rive.init()
        setContentView(R.layout.android_player)
        setResourceSpinner()
        loadResource(0)


    }

    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}
