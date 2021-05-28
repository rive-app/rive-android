package app.rive.runtime.example.utils

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatToggleButton
import app.rive.runtime.kotlin.RiveDrawable
import app.rive.runtime.kotlin.core.File

class RiveSwitch(context: Context, attrs: AttributeSet? = null) :

    AppCompatToggleButton(context, attrs) {

    var riveDrawable: RiveDrawable;
    var onAnimation: String;
    var offAnimation: String;
    var stateMachineName: String?;
    var booleanStateInput: String;

    private fun defaultedString(value: String?, default: String): String {
        value?.let {
            return it
        }
        return default
    }


    init {
        context.theme.obtainStyledAttributes(
            attrs,
            app.rive.runtime.example.R.styleable.RiveSwitch,
            0, 0
        ).apply {
            try {
                val resourceId = getResourceId(
                    app.rive.runtime.example.R.styleable.RiveSwitch_riveResource,
                    -1
                )
                stateMachineName = getString(app.rive.runtime.example.R.styleable.RiveSwitch_riveStateMachine);

                onAnimation = defaultedString(
                    getString(app.rive.runtime.example.R.styleable.RiveSwitch_riveOnAnimation),
                    "on"
                )
                offAnimation = defaultedString(
                    getString(app.rive.runtime.example.R.styleable.RiveSwitch_riveOffAnimation),
                    "off"
                )

                booleanStateInput = defaultedString(
                    getString(app.rive.runtime.example.R.styleable.RiveSwitch_riveBooleanStateInput),
                    "toggle"
                )

                var resourceBytes = resources.openRawResource(resourceId).readBytes()
                var riveFile = File(resourceBytes)
                riveDrawable = RiveDrawable(autoplay = false)
                riveDrawable.setRiveFile(riveFile)
                stateMachineName?.let{
                    riveDrawable.play(it, isStateMachine = true)
                    riveDrawable.setBooleanState(it, booleanStateInput, isChecked)
                }
                background = riveDrawable
            } finally {
                recycle()
            }
        }
    }

    private fun setCheckedAnimation(checked: Boolean){
        riveDrawable?.let{
            it.stop()
            if (checked) {
                it.play(onAnimation)
            } else {
                it.play(offAnimation)
            }
        }
    }

    private fun setStateMachine(checked: Boolean){
        riveDrawable?.let{ drawable ->
            stateMachineName?.let { stateMachine ->
                drawable.setBooleanState(stateMachine, booleanStateInput, checked)
            }
        }
    }

    override fun setChecked(checked: Boolean) {
        var output = super.setChecked(checked)

        if (stateMachineName == null){
            setCheckedAnimation(checked)
        }
        else {
            setStateMachine(checked)
        }

        return output
    }


    override fun getTextOn(): CharSequence {
        super.getTextOn()?.let{
            return it
        }
        return ""
    }
    override fun getTextOff(): CharSequence {
        super.getTextOn()?.let{
            return it
        }
        return ""
    }



}