package app.rive.runtime.example

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.File
import app.rive.runtime.kotlin.LinearAnimationInstance
import app.rive.runtime.kotlin.Renderer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var layout = LinearLayout(this);

        layout.orientation = LinearLayout.VERTICAL
        layout.weightSum = 2.0f

        var renderer = Renderer()

        var file = File(
            getResources().openRawResource(R.raw.flux_capacitor).readBytes()
        )
        var artboard = file.artboard()

        var simpleView = AnimationView(renderer, artboard, this)
        var animationCount = artboard.animationCount();
        for (i in 0 until animationCount){
            simpleView.animationInstances.add(
                LinearAnimationInstance(artboard.animation(i))
            )
        }

        var buttons = LinearLayout(this);
        buttons.orientation = LinearLayout.HORIZONTAL

        val btnTag = Button(this)
        val layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        btnTag.setLayoutParams(layoutParams)
        btnTag.setText("Pause")
        btnTag.setOnClickListener {
            simpleView.isPlaying = (!simpleView.isPlaying)
        }

        val resetBtnTag = Button(this)
        resetBtnTag.setLayoutParams(layoutParams)
        resetBtnTag.setText("Reset")
        resetBtnTag.setOnClickListener {
            simpleView.reset()
        }

        buttons.addView(btnTag)
        buttons.addView(resetBtnTag)
        layout.addView(buttons)
        layout.addView(simpleView)

        setContentView(layout);

    }
}
