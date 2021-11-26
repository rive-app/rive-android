package app.rive.runtime.example

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.example.utils.RiveButton


class ButtonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.button)

        var button = findViewById<RiveButton>(R.id.myButton)
        button.setOnClickListener {
            var textView = findViewById<TextView>(R.id.myButtonCounter)
            textView.text = (textView.text.toString().toInt() + 1).toString()
        }

//        var switch = findViewById<RiveSwitch>(R.id.mySwitch)
//        switch.setOnCheckedChangeListener { _, checked ->
//
//            var textView = findViewById<TextView>(R.id.mySwitchLabel)
//            textView.text = checked.toString()
//        }


//        var stateSwitch = findViewById<RiveSwitch>(R.id.myStateSwitch)
//        stateSwitch.setOnCheckedChangeListener { _, checked ->
//
//            var textView = findViewById<TextView>(R.id.myStateSwitchLabel)
//            textView.text = checked.toString()
//        }

    }
}
