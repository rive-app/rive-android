package app.rive.runtime.example

import android.os.Bundle
import android.util.Log
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

class FileCheater(val number: Int, val resource: Int)

object RiveFileDiffCallback : DiffUtil.ItemCallback<FileCheater>() {
    override fun areItemsTheSame(oldItem: FileCheater, newItem: FileCheater): Boolean {
        return oldItem.number == oldItem.number
    }

    override fun areContentsTheSame(oldItem: FileCheater, newItem: FileCheater): Boolean {
        return oldItem.number == oldItem.number
    }
}

class RiveAdapter : ListAdapter<FileCheater, RiveAdapter.RiveViewHolder>(RiveFileDiffCallback) {

    /* ViewHolder for Flower, takes in the inflated view and the onClick behavior. */
    class RiveViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val riveAnimationView: RiveAnimationView =
            itemView.findViewById(R.id.rive_animation_view)
        private var currentFile: FileCheater? = null

        /* Bind flower name and image. */
        fun bind(fileCheater: FileCheater) {
            currentFile = fileCheater
            riveAnimationView.setRiveResource(fileCheater.resource)
        }
    }

    /* Creates and inflates view and return FlowerViewHolder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_recycler_item, parent, false)
        return RiveViewHolder(view)
    }

    /* Gets current flower and uses it to bind view. */
    override fun onBindViewHolder(holder: RiveViewHolder, position: Int) {
        val fileCheater = getItem(position)
        Log.e("onBindViewHolder", "binding this file!")
        holder.bind(fileCheater)
    }

    override fun getItemCount(): Int {
        return 200
    }

    override fun getItem(position: Int): FileCheater {
        Log.e("GET_ITEM", "GET_ITEM ${Thread.activeCount()}")
        return FileCheater(position, R.raw.paff)
    }
}