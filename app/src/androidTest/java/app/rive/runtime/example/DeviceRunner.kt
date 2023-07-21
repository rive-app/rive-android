import org.junit.Assume.assumeFalse
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import org.junit.runners.model.Statement

annotation class OnDevice

class RunOnDevice(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    @Throws(InitializationError::class)
    constructor(testClass: Class<*>, notifier: RunNotifier) : this(testClass)

    override fun methodBlock(method: FrameworkMethod): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                // Check whether this is an emulator. If it is skip the test.
                assumeFalse(TestUtils.isProbablyRunningOnEmulator)
                // Continue with the usual test
                method.invokeExplosively(testClass.javaClass.newInstance())
            }
        }
    }
}