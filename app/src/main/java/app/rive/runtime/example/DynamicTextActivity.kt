package app.rive.runtime.example

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView

/**
 * Dynamically change a Rive Text Run value. In this example the run is named: "name"
 * For the run to be discoverable at runtime, the name as to be set in the editor.
 *
 * See: https://rive.app/community/doc/text/docn2E6y1lXo
 */
class DynamicTextActivity : AppCompatActivity(), TextWatcher {
    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.dynamic_text)
    }

    /**
     * Reference to a [RiveTextValueRun]
     */
    private val textRun by lazy(LazyThreadSafetyMode.NONE) {
        animationView.controller.activeArtboard?.textRun("name")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dynamic_text)
        val editText = findViewById<EditText>(R.id.text_run_value)
        editText.addTextChangedListener(this)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // get the current value of the reference
        textRun?.text?.let { Log.i("text-value-run", "Run before change: $it") }

        // or you can get the current value with:
        // animationView.getTextRunValue("name")
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // update the reference
        textRun?.text = s.toString()

        // or update the value using:
        // animationView.setTextRunValue("name", s.toString())
    }

    override fun afterTextChanged(s: Editable?) {
        textRun?.text?.let { Log.i("text-value-run", "Run after change: $it") }
    }
}
