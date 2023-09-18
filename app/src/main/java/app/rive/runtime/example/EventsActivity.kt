package app.rive.runtime.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.RiveGeneralEvent
import app.rive.runtime.kotlin.core.RiveOpenURLEvent
import app.rive.runtime.kotlin.core.RiveEvent
import java.lang.Exception


class EventsActivity : AppCompatActivity() {

    private val starRatingAnimation: RiveAnimationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.star_event_asset)
    }

    private val urlButtonAnimation: RiveAnimationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.url_event_button)
    }

    private val logButtonAnimation: RiveAnimationView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.log_event_button)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.events)
        setStarRating()
        setUrlButton()
        setLogButton()
    }

    override fun onDestroy() {
        starRatingAnimation.removeEventListener(starRatingListener);
        urlButtonAnimation.removeEventListener(urlButtonListener);
        logButtonAnimation.removeEventListener(logButtonListener);
        super.onDestroy()
    }

    private lateinit var starRatingListener: RiveFileController.RiveEventListener;
    private lateinit var urlButtonListener: RiveFileController.RiveEventListener;
    private lateinit var logButtonListener: RiveFileController.RiveEventListener;

    private fun setStarRating() {
        val starRatingTextView = findViewById<TextView>(R.id.star_rating)

        starRatingListener = object : RiveFileController.RiveEventListener {
            override fun notifyEvent(event: RiveEvent) {
                when (event) {
                    is RiveGeneralEvent -> {
                        Log.i("RiveEvent", "General event received, name: ${event.name}, delaySeconds: ${event.delay} properties: ${event.properties}")
                        runOnUiThread {
                            // This event contains a number value with the name "rating"
                            // to indicate the star rating selected
                            if (event.properties.containsKey("rating")) {
                                starRatingTextView.text =
                                    "Star rating: ${event.properties["rating"]}"
                            }
                        }
                    }
                }
            }
        }
        starRatingAnimation.addEventListener(starRatingListener);
    }

    private fun setUrlButton() {
        urlButtonListener = object : RiveFileController.RiveEventListener {
            override fun notifyEvent(event: RiveEvent) {
                when (event) {
                    is RiveOpenURLEvent -> {
                        Log.i("RiveEvent", "Open URL Rive event: ${event.url}")
                        runOnUiThread {
                            try {
                                val uri = Uri.parse(event.url);
                                val browserIntent =
                                    Intent(Intent.ACTION_VIEW, uri)
                                startActivity(browserIntent)
                            } catch (e: Exception) {
                                Log.i("RiveEvent", "Not a valid URL ${event.url}")
                            }
                        }
                    }
                }
            }
        }
        urlButtonAnimation.addEventListener(urlButtonListener);
    }

    private fun setLogButton() {
        logButtonListener = object : RiveFileController.RiveEventListener {
            override fun notifyEvent(event: RiveEvent) {
                when (event) {
                    is RiveOpenURLEvent -> {
                        Log.i("RiveEvent", "Open URL Rive event: ${event.url}")
                    }
                    is RiveGeneralEvent -> {
                        Log.i("RiveEvent", "General Rive event")
                    }
                }
                Log.i("RiveEvent", "name: ${event.name}")
                Log.i("RiveEvent", "delay: ${event.delay}")
                Log.i("RiveEvent", "type: ${event.type}")
                Log.i("RiveEvent", "properties: ${event.properties}")
                // `data` contains all information in the event
                Log.i("RiveEvent", "data: ${event.data}");
            }
        }
        logButtonAnimation.addEventListener(logButtonListener);
    }
}
