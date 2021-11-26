package app.rive.runtime.compatibilitytest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(applicationContext)
        setContentView(R.layout.activity_main)
    }
}