package app.rive.runtime.example

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.rive.runtime.example.databinding.ActivityAudioExternalAssetBinding
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.AudioAsset
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.RiveAudio

class AudioAssetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_audio_asset)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}

class AudioExternalAssetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioExternalAssetBinding
    private val audioDecoder = AudioDecoder(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioExternalAssetBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        val riveView = RiveAnimationView.Builder(this)
            .setAssetLoader(audioDecoder)
            .setResource(R.raw.ping_pong_audio_demo)
            .build()
        binding.main.addView(riveView)
    }

    private class AudioDecoder(private val context: Context) : FileAssetLoader() {
        lateinit var audioAsset: AudioAsset
        override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
            if (asset is AudioAsset) {
                val audioFilename = asset.uniqueFilename
                val rawResource = when {
                    audioFilename.startsWith("racket1") -> R.raw.racket1
                    audioFilename.startsWith("racket2") -> R.raw.racket2
                    audioFilename.startsWith("table") -> R.raw.table
                    else -> 0
                }

                if (rawResource <= 0) {
                    // Unknown resource...
                    return false
                }

                val audio = context
                    .resources
                    .openRawResource(rawResource)
                    .use {
                        RiveAudio.make(it.readBytes())
                    }
                asset.audio = audio
                return true
            }
            return false
        }
    }
}