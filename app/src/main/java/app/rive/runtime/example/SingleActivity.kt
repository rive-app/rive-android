package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SingleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isRiveRenderer = intent.getStringExtra("renderer") == "Rive"
        setContentView(
            if (isRiveRenderer)
                R.layout.single_rive_renderer
            else
                R.layout.single
        )
    }
}
