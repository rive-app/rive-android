package app.rive.runtime.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.example.databinding.ViewPagerBinding
import app.rive.runtime.example.databinding.ViewPagerRiveWrapperBinding
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement

@ControllerStateManagement
class ViewPagerActivity : AppCompatActivity() {
    private lateinit var binding: ViewPagerBinding

    companion object {
        const val TAG = "ViewPagerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ViewPagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load multiple pages in advance.
        binding.viewPager.offscreenPageLimit = 3
        binding.viewPager.apply {
            adapter = RiveViewPagerAdapter().apply {
                submitList(
                    listOf(
                        R.raw.flux_capacitor,
                        R.raw.off_road_car_blog,
                        R.raw.basketball,
                        R.raw.artboard_animations,
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure we clean up old values.
        val riveAdapter = binding.viewPager.adapter as RiveViewPagerAdapter
        riveAdapter.resourceCache.forEach { it?.dispose() }
    }

    private class RiveTestViewHolder(val binding: ViewPagerRiveWrapperBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindResource(@RawRes resId: Int) {
            binding.riveTestView.setRiveResource(resId)
        }
    }

    private class RiveViewPagerAdapter :
        ListAdapter<Int, RiveTestViewHolder>(object : DiffUtil.ItemCallback<Int>() {
            override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                return oldItem == newItem
            }
        }) {

        // Keep ControllerStates around.
        var resourceCache = arrayOfNulls<ControllerState>(itemCount)

        override fun onViewAttachedToWindow(holder: RiveTestViewHolder) {
            super.onViewAttachedToWindow(holder)
            val riveView = holder.binding.riveTestView
            val savedState = resourceCache[holder.layoutPosition]
            savedState?.let {
                riveView.restoreControllerState(it)
                // Once restored, we must clean up the reference.
                resourceCache[holder.layoutPosition] = null
            }
        }

        override fun onViewDetachedFromWindow(holder: RiveTestViewHolder) {
            val riveView = holder.binding.riveTestView
            val animationState = riveView.saveControllerState()
            resourceCache[holder.layoutPosition] = animationState

            super.onViewDetachedFromWindow(holder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiveTestViewHolder {
            val binding =
                ViewPagerRiveWrapperBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            return RiveTestViewHolder(binding)
        }

        override fun onCurrentListChanged(
            previousList: MutableList<Int>,
            currentList: MutableList<Int>,
        ) {
            super.onCurrentListChanged(previousList, currentList)
            // Clean up old values.
            resourceCache.forEach { it?.dispose() }
            // Reallocate the cache if the list has changed.
            // This can be refined further and be reallocated only if the resources have changed.
            resourceCache = arrayOfNulls(itemCount)
        }

        override fun onBindViewHolder(holder: RiveTestViewHolder, position: Int) {
            getItem(position).let {
                holder.bindResource(it)
            }
        }
    }
}
