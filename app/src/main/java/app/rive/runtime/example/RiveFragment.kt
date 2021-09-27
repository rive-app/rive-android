package app.rive.runtime.example

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import kotlin.properties.Delegates

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
public const val RIVE_FRAGMENT_ARG_RES_ID = "resourceId"

/**
 * A [RiveFragment] encapsulates a [RiveAnimationView].
 * Use the [RiveFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class RiveFragment : Fragment() {
    private var rId: Int by Delegates.notNull()

    private val riveView by lazy(LazyThreadSafetyMode.NONE) {
        requireView().findViewById<RiveAnimationView>(R.id.rive_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            rId = it.getInt(RIVE_FRAGMENT_ARG_RES_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rive, container, false)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        riveView.setRiveResource(rId)
        riveView.fit = Fit.COVER
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param resId the resource id of the Rive file.
         * @return A new instance of RiveFragment.
         */
        @JvmStatic
        fun newInstance(resId: Int) =
                RiveFragment().apply {
                    arguments = Bundle().apply {
                        putInt(RIVE_FRAGMENT_ARG_RES_ID, resId)
                    }
                }
    }
}