package app.rive

import android.util.Log

/** [RiveLog.Logger] implementation that logs to Logcat with lazy `msg()` evaluation. */
class LogcatLogger : RiveLog.Logger {
    override fun v(tag: String, msg: () -> String) {
        Log.v(tag, msg())
    }

    override fun d(tag: String, msg: () -> String) {
        Log.d(tag, msg())
    }

    override fun i(tag: String, msg: () -> String) {
        Log.i(tag, msg())
    }

    override fun w(tag: String, msg: () -> String) {
        Log.w(tag, msg())
    }

    override fun e(tag: String, t: Throwable?, msg: () -> String) {
        Log.e(tag, msg(), t)
    }
}
