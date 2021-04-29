package app.rive.runtime.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit

class RiveFragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rive_fragment)

        // Instantiate the basketball fragment
        var bundle = bundleOf(RIVE_FRAGMENT_ARG_RES_ID to R.raw.basketball)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<RiveFragment>(R.id.basketball_fragment, args = bundle)
            }
        }

        // Instantiate the flux fragment
        bundle = bundleOf(RIVE_FRAGMENT_ARG_RES_ID to R.raw.flux_capacitor)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<RiveFragment>(R.id.flux_fragment, args = bundle)
            }
        }
    }
}
