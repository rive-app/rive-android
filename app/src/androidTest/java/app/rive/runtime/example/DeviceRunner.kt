package app.rive.runtime.example

import org.junit.Assume.assumeFalse
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

annotation class OnDevice

class RunOnDevice(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    override fun methodBlock(method: FrameworkMethod): Statement {
        return object : Statement() {
            @Throws(Exception::class)
            override fun evaluate() {
                // Check whether this is an emulator. If it is skip the test.
                assumeFalse(TestUtils.isProbablyRunningOnEmulator)
                // Continue with the usual test
                method.invokeExplosively(testClass.javaClass.getDeclaredConstructor().newInstance())
            }
        }
    }
}
