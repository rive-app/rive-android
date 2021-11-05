package app.rive.runtime.kotlin.core

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.core.Rive
import org.junit.Assert.assertEquals


fun initTests(): Context {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

    Rive.init(appContext)
    return appContext
}
