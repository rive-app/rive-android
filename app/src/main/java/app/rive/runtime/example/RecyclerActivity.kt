package app.rive.runtime.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.core.File

@ControllerStateManagement
class RecyclerActivity : AppCompatActivity() {

    companion object {
        const val holderCount = 200
        const val numCols = 3

        // Either use the shared file (true) or initialize in the adapter (false)
        const val useSharedFile = true
    }

    lateinit var sharedFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler)

        sharedFile = resources.openRawResource(R.raw.basketball).use { File(it.readBytes()) }

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = RiveAdapter(sharedFile, useSharedFile)
        recyclerView.layoutManager = GridLayoutManager(this, numCols)
    }

    override fun onDestroy() {
        val riveAdapter: RiveAdapter =
            findViewById<RecyclerView>(R.id.recycler_view).adapter as RiveAdapter
        sharedFile.release()
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
class RiveAdapter(private val sharedFile: File, private val useSharedFile: Boolean) :
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

    private fun initialize(holder: RiveViewHolder) {
        if (useSharedFile) {
            holder.riveAnimationView.setRiveFile(sharedFile)
        } else {
            val riveFileResource = getItem(holder.adapterPosition)
            holder.bind(riveFileResource)
        }
    }

    private fun restoreControllerAt(holder: RiveViewHolder): Boolean {
        val position = holder.adapterPosition
        if (resourceCache[position] == null) {
            return false
        }

        val savedState = resourceCache[position]
        savedState?.let {
            holder.riveAnimationView.restoreControllerState(it)
            // Once restored clean up the reference.
            resourceCache[position] = null
        }
        return true
    }

    override fun onBindViewHolder(holder: RiveViewHolder, position: Int) {
        // Alternate background colors to differentiate various elements.
        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor("#FFFFFF".toColorInt())
        } else {
            holder.itemView.setBackgroundColor("#FFFAF8FD".toColorInt())
        }
    }

    override fun onViewAttachedToWindow(holder: RiveViewHolder) {
        super.onViewAttachedToWindow(holder)

        if (!restoreControllerAt(holder)) {
            initialize(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: RiveViewHolder) {
        // Before detaching, let's save the Controller state.
        val savedState = holder.riveAnimationView.saveControllerState()
        resourceCache[holder.adapterPosition] = savedState
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
