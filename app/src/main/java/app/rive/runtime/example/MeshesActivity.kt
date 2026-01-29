package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.rive.runtime.example.utils.setEdgeToEdgeContent

class MeshesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContent(R.layout.activity_meshes)
    }
}
