package app.rive.runtime.example

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView

class RecyclerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler)


        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = RiveAdapter()
    }
}

// Holds a rive file resource for a RecyclerView list item.
data class RiveFileResource(val number: Int, val resource: Int)

object RiveFileDiffCallback : DiffUtil.ItemCallback<RiveFileResource>() {
    override fun areItemsTheSame(oldItem: RiveFileResource, newItem: RiveFileResource): Boolean {
        return oldItem.number == newItem.number
    }

    override fun areContentsTheSame(oldItem: RiveFileResource, newItem: RiveFileResource): Boolean {
        return oldItem.number == newItem.number
    }
}

class RiveAdapter :
    ListAdapter<RiveFileResource, RiveAdapter.RiveViewHolder>(RiveFileDiffCallback) {

    class RiveViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val riveAnimationView: RiveAnimationView =
            itemView.findViewById(R.id.rive_animation_view)
        private var currentFile: RiveFileResource? = null

        fun bind(riveFileResource: RiveFileResource) {
            currentFile = riveFileResource
            riveAnimationView.setRiveResource(riveFileResource.resource)
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
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFAF8FD"));
        }
        val riveFileResource = getItem(position)
        holder.bind(riveFileResource)
    }

    override fun getItemCount(): Int {
        return 200
    }

    override fun getItem(position: Int): RiveFileResource {
        val res = if (position % 2 == 1) {
            R.raw.basketball
        } else {
            R.raw.circle_move
        }
        return RiveFileResource(position, res)
    }
}