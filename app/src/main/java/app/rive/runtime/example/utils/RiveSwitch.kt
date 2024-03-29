package app.rive.runtime.example.utils

/*
class RiveSwitch(context: Context, attrs: AttributeSet? = null) :

    AppCompatToggleButton(context, attrs) {

    var riveArtboardRenderer: RiveArtboardRenderer;
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
                stateMachineName =
                    getString(app.rive.runtime.example.R.styleable.RiveSwitch_riveStateMachine);

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
                riveArtboardRenderer = RiveArtboardRenderer(autoplay = false)
                riveArtboardRenderer.setRiveFile(riveFile)
                stateMachineName?.let {
                    riveArtboardRenderer.controller.setBooleanState(it, booleanStateInput, isChecked)
                    riveArtboardRenderer.controller.play(it, isStateMachine = true)
                }
//                background = riveDrawable
            } finally {
                recycle()
            }
        }
    }

    private fun setCheckedAnimation(checked: Boolean) {
        riveArtboardRenderer.controller.let {
            it.stopAnimations()
            if (checked) {
                it.play(onAnimation)
            } else {
                it.play(offAnimation)
            }
        }
    }

    private fun setStateMachine(checked: Boolean) {
        riveArtboardRenderer.let { drawable ->
            stateMachineName?.let { stateMachine ->
                drawable.controller.setBooleanState(stateMachine, booleanStateInput, checked)
            }
        }
    }

    override fun setChecked(checked: Boolean) {
        val output = super.setChecked(checked)

        if (stateMachineName == null) {
            setCheckedAnimation(checked)
        } else {
            setStateMachine(checked)
        }

        return output
    }


    override fun getTextOn(): CharSequence {
        super.getTextOn()?.let {
            return it
        }
        return ""
    }

    override fun getTextOff(): CharSequence {
        super.getTextOn()?.let {
            return it
        }
        return ""
    }


}
 */