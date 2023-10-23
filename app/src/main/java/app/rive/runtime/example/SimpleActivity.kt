package app.rive.runtime.example

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import app.rive.runtime.example.databinding.SimpleBinding

class SimpleActivity : AppCompatActivity() {

    private lateinit var binding : SimpleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
