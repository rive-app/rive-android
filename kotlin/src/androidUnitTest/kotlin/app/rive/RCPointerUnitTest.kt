package app.rive

import app.rive.core.RCPointer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.concurrent.thread

const val TEST_POINTER_ADDRESS = 12345L
const val TEST_LABEL = "TestObject"
const val TEST_SRC = "TestSource"
const val TEST_REASON = "Test reason"

class RCPointerUnitTest : FunSpec({
    val deletedPointers = mutableListOf<Long>()
    val onDisposeCallback: (Long) -> Unit = { pointer ->
        deletedPointers.add(pointer)
    }
    lateinit var rcPointer: RCPointer

    beforeTest {
        deletedPointers.clear()
        rcPointer = RCPointer(TEST_POINTER_ADDRESS, TEST_LABEL, onDisposeCallback)
    }

    context("Initialization") {
        test("Constructor initializes with ref count of 1") {
            rcPointer.refCount shouldBe 1
        }

        test("Constructor initializes with isDisposed false") {
            rcPointer.isDisposed shouldBe false
        }

        test("Constructor stores pointer correctly") {
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
        }

        test("Pointer can be accessed multiple times when not disposed") {
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
        }
    }

    context("Basic acquire/release behavior") {
        test("Acquire increases ref count") {
            rcPointer.acquire(TEST_SRC)

            rcPointer.refCount shouldBe 2
        }

        test("Release decreases ref count") {
            rcPointer.acquire("Source1")
            rcPointer.release("Source1")

            rcPointer.refCount shouldBe 1
        }

        test("Acquire can be called multiple times") {
            rcPointer.acquire("Source1")
            rcPointer.refCount shouldBe 2
            rcPointer.acquire("Source2")
            rcPointer.refCount shouldBe 3
            rcPointer.acquire("Source3")
            rcPointer.refCount shouldBe 4
        }

        test("Multiple acquire and release operations maintain correct ref count") {
            rcPointer.acquire("Source1")
            rcPointer.refCount shouldBe 2
            rcPointer.acquire("Source2")
            rcPointer.refCount shouldBe 3
            rcPointer.release("Source1")
            rcPointer.refCount shouldBe 2
            rcPointer.acquire("Source3")
            rcPointer.refCount shouldBe 3
            rcPointer.release("Source2")
            rcPointer.refCount shouldBe 2
        }

        test("Pointer is accessible between acquire and release") {
            rcPointer.acquire("Source1")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.release("Source1")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.acquire("Source2")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.release("Source2")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            // Multiple acquires
            rcPointer.acquire("Source3")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.acquire("Source4")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.release("Source3")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
            rcPointer.release("Source4")
            rcPointer.pointer shouldBe TEST_POINTER_ADDRESS
        }

        test("Release with empty reason works") {
            rcPointer.acquire("Source1")
            rcPointer.release("Source1", "")

            rcPointer.refCount shouldBe 1
        }

        test("Release with non-empty reason works") {
            rcPointer.acquire("Source1")
            rcPointer.release("Source1", TEST_REASON)

            rcPointer.refCount shouldBe 1
        }
    }

    context("Release to 0 behavior") {
        test("Release to 0 calls onDelete callback") {
            rcPointer.release(TEST_SRC)

            deletedPointers.size shouldBe 1
            deletedPointers[0] shouldBe TEST_POINTER_ADDRESS

            val customPointer = 99999L
            val customRcPointer = RCPointer(customPointer, "CustomObject", onDisposeCallback)

            customRcPointer.release(TEST_SRC)

            deletedPointers.size shouldBe 2
            deletedPointers[1] shouldBe customPointer
        }

        test("Release to 0 sets isDisposed to true") {
            rcPointer.release(TEST_SRC)

            rcPointer.refCount shouldBe 0
            rcPointer.isDisposed shouldBe true
        }

        test("Release does not call onDelete when ref count is greater than 1") {
            rcPointer.acquire("Source1")
            rcPointer.acquire("Source2")
            rcPointer.release("Source1")

            deletedPointers.size shouldBe 0
            rcPointer.isDisposed shouldBe false
        }

        test("Release calls onDelete only when ref count reaches 0") {
            rcPointer.acquire("Source1")
            rcPointer.acquire("Source2")
            rcPointer.release("Source1") // ref count: 2
            deletedPointers.size shouldBe 0

            rcPointer.release("Source2") // ref count: 1
            deletedPointers.size shouldBe 0

            rcPointer.release("Source3") // ref count: 0
            deletedPointers.size shouldBe 1
            deletedPointers[0] shouldBe TEST_POINTER_ADDRESS
        }

        test("Disposed remains false until ref count reaches zero") {
            rcPointer.acquire("Source1")
            rcPointer.isDisposed shouldBe false

            rcPointer.release("Source1")
            rcPointer.isDisposed shouldBe false

            rcPointer.release("Source2")
            rcPointer.isDisposed shouldBe true
        }
    }

    context("Access after disposal") {
        test("Pointer throws when accessed after disposal") {
            rcPointer.release(TEST_SRC)

            val exception = shouldThrow<IllegalStateException> {
                rcPointer.pointer
            }
            exception.message shouldContain TEST_LABEL
        }

        test("Acquire throws when called after disposal") {
            rcPointer.release(TEST_SRC)

            val exception = shouldThrow<IllegalStateException> {
                rcPointer.acquire(TEST_SRC)
            }
            exception.message shouldContain TEST_LABEL
        }

        test("Release does not call onDelete if ref count was already 0") {
            rcPointer.release(TEST_SRC)
            deletedPointers.clear()

            shouldThrow<IllegalStateException> {
                rcPointer.release(TEST_SRC)
            }

            deletedPointers.size shouldBe 0
        }

        test("Release throws with label, reason, and source") {
            rcPointer.release(TEST_SRC)

            val exception = shouldThrow<IllegalStateException> {
                rcPointer.release(TEST_SRC, "Test reason")
            }

            exception.message shouldContain TEST_LABEL
            exception.message shouldContain TEST_SRC
            exception.message shouldContain TEST_REASON
        }
    }

    context("Concurrency") {
        test("Concurrent acquire operations increase ref count correctly (balanced)") {
            val threadCount = 30
            val iterations = 10000

            val threads = List(threadCount) { threadIdx ->
                thread(start = true, name = "Thread$threadIdx") {
                    for (i in 1..iterations) {
                        rcPointer.acquire("Thread$threadIdx $i")
                        rcPointer.release("Thread$threadIdx $i")
                    }
                }
            }
            threads.forEach { it.join() }

            val expected = 1
            rcPointer.refCount shouldBe expected
            rcPointer.isDisposed shouldBe false
        }

        test("Concurrent acquire operations increase ref count correctly (unbalanced)") {
            val threadCount = 30
            val iterations = 10000

            val threads = List(threadCount) { threadIdx ->
                thread(start = true, name = "Thread$threadIdx") {
                    for (i in 1..iterations) {
                        rcPointer.acquire("Thread$threadIdx $i")
                        if (i % 2 == 0) {
                            rcPointer.release("Thread$threadIdx $i")
                        }
                    }
                }
            }
            threads.forEach { it.join() }

            val expected = 1 + (iterations / 2) * threadCount
            rcPointer.refCount shouldBe expected
            rcPointer.isDisposed shouldBe false
        }

        test("Concurrent release to zero disposes exactly once") {
            // Start with higher refcount
            val initialExtraRefs = 100
            repeat(initialExtraRefs) {
                rcPointer.acquire("init-$it")
            }

            val threads = List(initialExtraRefs + 10) { tIndex ->
                thread(start = true, name = "RelThread-$tIndex") {
                    // Each thread attempts at least one release
                    try {
                        rcPointer.release("thread-$tIndex")
                    } catch (_: IllegalStateException) {
                        // Some threads will hit negative and throw, that's expected
                    }
                }
            }
            threads.forEach { it.join() }

            deletedPointers.size shouldBe 1
            deletedPointers[0] shouldBe TEST_POINTER_ADDRESS
            rcPointer.isDisposed shouldBe true
        }
    }

    context("Logging") {
        val logs = mutableListOf<String>()
        RiveLog.logger = object : RiveLog.Logger {
            override fun d(tag: String, msg: () -> String) {
                logs.add(msg())
            }

            override fun v(tag: String, msg: () -> String) {
                logs.add(msg())
            }

            override fun i(tag: String, msg: () -> String) {
                logs.add(msg())
            }

            override fun w(tag: String, msg: () -> String) {
                logs.add(msg())
            }

            override fun e(tag: String, t: Throwable?, msg: () -> String) {
                logs.add(msg())
            }
        }

        afterTest {
            logs.clear()
        }

        test("Acquire and release produce logs") {

            rcPointer.acquire(TEST_SRC)
            rcPointer.release(TEST_SRC, TEST_REASON)

            logs[0] shouldContain TEST_LABEL
            logs[0] shouldContain TEST_SRC

            logs[1] shouldContain TEST_LABEL
            logs[1] shouldContain TEST_SRC
            logs[1] shouldContain TEST_REASON
        }

        test("Dispose produces log") {
            rcPointer.release(TEST_SRC)

            logs[0] shouldContain TEST_LABEL
        }
    }
})
