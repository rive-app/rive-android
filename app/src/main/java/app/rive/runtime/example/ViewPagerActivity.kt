package app.rive.runtime.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.example.databinding.ViewPagerBinding
import app.rive.runtime.example.databinding.ViewPagerRiveWrapperBinding


class ViewPagerActivity : AppCompatActivity() {
    private lateinit var binding: ViewPagerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ViewPagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.apply {
            adapter = RiveViewPagerAdapter().apply {
                submitList(
                    listOf(
                        R.raw.basketball,
                        R.raw.off_road_car_blog,
                        R.raw.flux_capacitor,
                        R.raw.artboard_animations,
                    )
                )
            }
        }
    }

    private class RiveTestViewHolder(val binding: ViewPagerRiveWrapperBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(res: Int) {
            binding.riveTestView.setRiveResource(res)
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
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiveTestViewHolder {
            val binding =
                ViewPagerRiveWrapperBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            return RiveTestViewHolder(binding)
        }

        override fun onBindViewHolder(holder: RiveTestViewHolder, position: Int) {
            getItem(position).let {
                holder.bind(it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        // menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            // R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
