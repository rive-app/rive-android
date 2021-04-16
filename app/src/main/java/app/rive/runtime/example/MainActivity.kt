package app.rive.runtime.example

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.example_selection)

        findViewById<Button>(R.id.go_animations).setOnClickListener {
            startActivity(
                Intent(this, AnimationsActivity::class.java)
            )
        }
        findViewById<Button>(R.id.go_artboards).setOnClickListener {
            startActivity(
                Intent(this, ArtboardActivity::class.java)
            )
        }

        findViewById<Button>(R.id.go_buggy).setOnClickListener {
            startActivity(
                Intent(this, BuggyActivity::class.java)
            )
        }

    }
}
