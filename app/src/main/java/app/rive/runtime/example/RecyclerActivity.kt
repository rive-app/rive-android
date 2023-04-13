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
import app.rive.runtime.kotlin.core.File

class RecyclerActivity : AppCompatActivity() {

    companion object {
        const val TAG = "RecyclerActivity"
        const val holderCount = 200
        const val useIds = false
    }

    private val riveFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler)

        val basketStream = resources.openRawResource(R.raw.basketball)
        val basketBytes = basketStream.readBytes()
        val circleStream = resources.openRawResource(R.raw.circle_move)
        val circleBytes = circleStream.readBytes()

        // Init all the elements.
        repeat(holderCount) {
            val bytes = if (it % 2 == 1) {
                basketBytes
            } else {
                circleBytes
            }
            riveFiles.add(File(bytes))
        }

        basketStream.close()
        circleStream.close()

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = RiveAdapter(riveFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onDestroy() {
        // Dereference all the files we initialized.
        riveFiles.forEach {
            it.release()
        }
        super.onDestroy()
    }
}

// Holds a rive file resource for a RecyclerView list item.
interface RiveResource {
    val number: Int
    val resource: Any
}

data class RiveIdResource(override val number: Int, @RawRes override val resource: Int) :
    RiveResource

data class RiveFileResource(override val number: Int, override val resource: File) : RiveResource

object RiveFileDiffCallback : DiffUtil.ItemCallback<RiveResource>() {
    override fun areItemsTheSame(oldItem: RiveResource, newItem: RiveResource): Boolean {
        return oldItem.number == newItem.number
    }

    override fun areContentsTheSame(oldItem: RiveResource, newItem: RiveResource): Boolean {
        return oldItem.number == newItem.number
    }
}

class RiveAdapter(val riveFiles: List<File>) :
    ListAdapter<RiveResource, RiveAdapter.RiveViewHolder>(RiveFileDiffCallback) {

    class RiveViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val riveAnimationView: RiveAnimationView =
            itemView.findViewById(R.id.rive_animation_view)
        private var currentResource: RiveResource? = null

        fun bind(riveFileResource: RiveResource) {
            currentResource = riveFileResource
            when (riveFileResource) {
                is RiveIdResource -> riveAnimationView.setRiveResource(riveFileResource.resource)
                is RiveFileResource -> riveAnimationView.setRiveFile(riveFileResource.resource)
            }
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
        return RecyclerActivity.holderCount
    }

    override fun getItem(position: Int): RiveResource {
        if (RecyclerActivity.useIds) {
            val res = if (position % 2 == 1) {
                R.raw.basketball
            } else {
                R.raw.circle_move
            }
            return RiveIdResource(position, res)
        }

        return RiveFileResource(position, riveFiles[position])
    }
}