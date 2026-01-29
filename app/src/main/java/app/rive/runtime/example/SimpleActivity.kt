package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.rive.runtime.example.databinding.SimpleBinding
import app.rive.runtime.example.utils.setEdgeToEdgeContent

class SimpleActivity : ComponentActivity() {

    private lateinit var binding: SimpleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SimpleBinding.inflate(layoutInflater)
        setEdgeToEdgeContent(binding.root)
    }
}
