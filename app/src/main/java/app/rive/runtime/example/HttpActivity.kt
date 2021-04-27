package app.rive.runtime.example

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Rive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class HttpActivity : AppCompatActivity() {
    // Url of the Rive file
    private val riveUrl = "https://cdn.rive.app/animations/juice_v7.riv"

    // Rive animation view, from the layout xml
    private val animationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<RiveAnimationView>(R.id.http_view)
    }

    // ViewModel for fetching http data asynchronously
    private val httpViewModel by viewModels<HttpViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init()
        setContentView(R.layout.activity_http)

        // Hides the app/action bar
        supportActionBar?.hide();

        // Load the Rive data asynchronously
        httpViewModel.byteLiveData.observe(
            this,
            Observer { bytes ->
                // Pass the Rive file bytes to the animation view
                animationView.setRiveBytes(
                    bytes,
                    // Fit the animation to the cover the entire view
                    fit = Fit.COVER
                )
            }
        )
        httpViewModel.fetchUrl(riveUrl)
    }

    // Clean up the animation view
    override fun onDestroy() {
        super.onDestroy()
        animationView.destroy()
    }
}

// ViewModel for asynchronously fetching Rive file data
// Using a 3rd party http library is recommended
class HttpViewModel : ViewModel() {
    val byteLiveData = MutableLiveData<ByteArray>()

    fun fetchUrl(url: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                fetchAsync(url)
            }
        }
    }

    private fun fetchAsync(url: String) {
        byteLiveData.postValue(URL(url).openStream().use { it.readBytes() })
    }
}