package app.rive.runtime.kotlin

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals


fun initTests(): Context {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

    Rive.init()
    return appContext
}
