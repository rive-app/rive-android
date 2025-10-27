package app.rive.runtime.example

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.example.utils.RiveButton

class ButtonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.button)

        val button = findViewById<RiveButton>(R.id.myButton)
        button.setOnClickListener {
            val counterText = findViewById<TextView>(R.id.myButtonCounter)
            counterText.text = (counterText.text.toString().toInt() + 1).toString()
        }
    }
}
