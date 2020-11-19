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
            getResources().openRawResource(R.raw.off_road_car_blog).readBytes()
        )
        var artboard = file.artboard()

        var simpleView = AnimationView(renderer, artboard, this)

        simpleView.animationInstances.add(
            LinearAnimationInstance(artboard.animation(0))
        )
        simpleView.animationInstances.add(
            LinearAnimationInstance(artboard.animation(1))
        )
        simpleView.animationInstances.add(
            LinearAnimationInstance(artboard.animation(2))
        )
        simpleView.animationInstances.add(
            LinearAnimationInstance(artboard.animation(3))
        )

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

        layout.addView(btnTag)
        layout.addView(simpleView);

        setContentView(layout);

    }
}
