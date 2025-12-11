package app.rive.snapshot

import android.graphics.Bitmap
import java.util.concurrent.CountDownLatch

/** Interface for snapshot activities that produce a bitmap result. */
interface SnapshotActivityResult {
    /** The rendered bitmap result. */
    var resultBitmap: Bitmap

    /** Latch that signals when the bitmap result is ready. */
    val resultLatch: CountDownLatch
}
