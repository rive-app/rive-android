package app.rive.runtime.example

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.RiveButton
import app.rive.runtime.example.utils.setEdgeToEdgeContent

class ButtonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.button)

        val button = findViewById<RiveButton>(R.id.myButton)
        button.setOnClickListener {
            val counterText = findViewById<TextView>(R.id.myButtonCounter)
            counterText.text = (counterText.text.toString().toInt() + 1).toString()
        }
    }
}
