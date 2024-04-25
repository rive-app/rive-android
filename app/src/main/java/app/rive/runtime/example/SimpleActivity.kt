package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.example.databinding.SimpleBinding

class SimpleActivity : AppCompatActivity() {

    private lateinit var binding: SimpleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
