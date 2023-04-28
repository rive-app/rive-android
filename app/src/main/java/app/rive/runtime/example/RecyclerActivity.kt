package app.rive.runtime.example

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement

@ControllerStateManagement
class RecyclerActivity : AppCompatActivity() {

    companion object {
        const val holderCount = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler)

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = RiveAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onDestroy() {
        val riveAdapter: RiveAdapter =
            findViewById<RecyclerView>(R.id.recycler_view).adapter as RiveAdapter
        riveAdapter.resourceCache.forEach { it?.dispose() }
        super.onDestroy()
    }
}

data class RiveResource(@RawRes val id: Int)

object RiveFileDiffCallback : DiffUtil.ItemCallback<RiveResource>() {
    override fun areItemsTheSame(oldItem: RiveResource, newItem: RiveResource): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: RiveResource, newItem: RiveResource): Boolean {
        return oldItem == newItem
    }
}

@ControllerStateManagement
class RiveAdapter :
    ListAdapter<RiveResource, RiveAdapter.RiveViewHolder>(RiveFileDiffCallback) {

    // Keep ControllerStates around.
    internal val resourceCache =
        arrayOfNulls<ControllerState>(RecyclerActivity.holderCount)

    class RiveViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        internal val riveAnimationView: RiveAnimationView =
            itemView.findViewById(R.id.rive_animation_view)

        fun bind(riveResource: RiveResource) {
            riveAnimationView.setRiveResource(riveResource.id)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_recycler_item, parent, false)
        return RiveViewHolder(view)
    }

    override fun onBindViewHolder(holder: RiveViewHolder, position: Int) {
        // Alternate background colors to differentiate various elements.
        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFAF8FD"))
        }
        val riveFileResource = getItem(position)
        holder.bind(riveFileResource)
    }

    override fun onViewAttachedToWindow(holder: RiveViewHolder) {
        super.onViewAttachedToWindow(holder)
        val savedState = resourceCache[holder.layoutPosition]
        savedState?.let {
            holder.riveAnimationView.restoreControllerState(it)
            // Once restored, we must clean up the reference.
            resourceCache[holder.layoutPosition] = null
        }
    }

    override fun onViewDetachedFromWindow(holder: RiveViewHolder) {
        // Before detaching, let's save the Controller state.
        val savedState = holder.riveAnimationView.saveControllerState()
        resourceCache[holder.layoutPosition] = savedState
        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemCount(): Int {
        return RecyclerActivity.holderCount
    }

    override fun getItem(position: Int): RiveResource {
        val res = if (position % 2 == 1) {
            R.raw.basketball
        } else {
            R.raw.circle_move
        }
        return RiveResource(res)
    }
}