package app.rive.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

private const val STATE_MACHINE_HANDLE = 123L
private const val SECOND_STATE_MACHINE_HANDLE = 456L

class StateMachineSettlingStoreTest : FunSpec({
    test("A registered state machine starts unsettled and accepts a newer callback") {
        val nextRequestID = AtomicLong()
        val store = StateMachineSettlingStore(nextRequestID::getAndIncrement)
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)

        store.register(stateMachineHandle)
        val settled = store.settled(stateMachineHandle)
        val advanceRequestID = nextRequestID.getAndIncrement()

        settled.value shouldBe false
        store.settle(advanceRequestID, stateMachineHandle)
        settled.value shouldBe true
    }

    test("Unsettling rejects callbacks at or before the new request ID boundary") {
        val nextRequestID = AtomicLong()
        val store = StateMachineSettlingStore(nextRequestID::getAndIncrement)
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        store.register(stateMachineHandle)
        val settled = store.settled(stateMachineHandle)
        val previousAdvanceRequestID = nextRequestID.getAndIncrement()
        store.settle(previousAdvanceRequestID, stateMachineHandle)

        store.unsettle(stateMachineHandle)
        val unsettledBoundary = nextRequestID.get() - 1

        settled.value shouldBe false
        store.settle(previousAdvanceRequestID, stateMachineHandle)
        settled.value shouldBe false
        store.settle(unsettledBoundary, stateMachineHandle)
        settled.value shouldBe false

        val currentAdvanceRequestID = nextRequestID.getAndIncrement()
        store.settle(currentAdvanceRequestID, stateMachineHandle)
        settled.value shouldBe true
    }

    test("A settled callback cannot overtake an in-progress unsettle") {
        val nextRequestID = AtomicLong()
        val unsettledReservationStarted = CountDownLatch(1)
        val releaseUnsettle = CountDownLatch(1)
        val callbackStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val store = StateMachineSettlingStore {
            nextRequestID.getAndIncrement().also { requestID ->
                if (requestID == 2L) {
                    unsettledReservationStarted.countDown()
                    check(releaseUnsettle.await(5, TimeUnit.SECONDS)) {
                        "Timed out waiting to finish the unsettled request ID reservation"
                    }
                }
            }
        }
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        store.register(stateMachineHandle) // Request 0 establishes the initial boundary.
        val previousAdvanceRequestID = nextRequestID.getAndIncrement() // Request 1.
        store.settle(previousAdvanceRequestID, stateMachineHandle)
        val settled = store.settled(stateMachineHandle)
        settled.value shouldBe true

        try {
            val unsettle = executor.submit {
                store.unsettle(stateMachineHandle) // Pauses while reserving request 2.
            }
            unsettledReservationStarted.await(5, TimeUnit.SECONDS) shouldBe true

            val staleCallback = executor.submit {
                callbackStarted.countDown()
                store.settle(previousAdvanceRequestID, stateMachineHandle)
            }
            callbackStarted.await(5, TimeUnit.SECONDS) shouldBe true

            shouldThrow<TimeoutException> {
                staleCallback.get(250, TimeUnit.MILLISECONDS)
            }

            releaseUnsettle.countDown()
            unsettle.get(5, TimeUnit.SECONDS)
            staleCallback.get(5, TimeUnit.SECONDS)
            settled.value shouldBe false
        } finally {
            releaseUnsettle.countDown()
            executor.shutdownNow()
        }
    }

    test("Registering the same state machine twice fails") {
        val store = StateMachineSettlingStore(AtomicLong()::getAndIncrement)
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        store.register(stateMachineHandle)

        shouldThrow<IllegalStateException> {
            store.register(stateMachineHandle)
        }
    }

    test("Unregistering publishes terminal settled state and ignores later callbacks") {
        val store = StateMachineSettlingStore(AtomicLong()::getAndIncrement)
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        store.register(stateMachineHandle)
        val settled = store.settled(stateMachineHandle)

        store.unregister(stateMachineHandle)
        store.settle(Long.MAX_VALUE, stateMachineHandle)

        settled.value shouldBe true
        shouldThrow<IllegalStateException> {
            store.settled(stateMachineHandle)
        }
        shouldThrow<IllegalStateException> {
            store.unsettle(stateMachineHandle)
        }
    }

    test("A new registration rejects callbacks from an earlier use of its handle") {
        val nextRequestID = AtomicLong()
        val store = StateMachineSettlingStore(nextRequestID::getAndIncrement)
        val stateMachineHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        store.register(stateMachineHandle)
        val previousSettled = store.settled(stateMachineHandle)
        val previousAdvanceRequestID = nextRequestID.getAndIncrement()
        store.unregister(stateMachineHandle)

        store.register(stateMachineHandle)
        val settled = store.settled(stateMachineHandle)
        store.settle(previousAdvanceRequestID, stateMachineHandle)

        previousSettled.value shouldBe true
        settled.value shouldBe false
        val currentAdvanceRequestID = nextRequestID.getAndIncrement()
        store.settle(currentAdvanceRequestID, stateMachineHandle)
        settled.value shouldBe true
    }

    test("Clearing publishes terminal settled state for every registration") {
        val store = StateMachineSettlingStore(AtomicLong()::getAndIncrement)
        val firstHandle = StateMachineHandle(STATE_MACHINE_HANDLE)
        val secondHandle = StateMachineHandle(SECOND_STATE_MACHINE_HANDLE)
        store.register(firstHandle)
        store.register(secondHandle)
        val firstSettled = store.settled(firstHandle)
        val secondSettled = store.settled(secondHandle)

        store.clear()
        store.settle(Long.MAX_VALUE, firstHandle)
        store.settle(Long.MAX_VALUE, secondHandle)

        firstSettled.value shouldBe true
        secondSettled.value shouldBe true
        shouldThrow<IllegalStateException> {
            store.settled(firstHandle)
        }
        shouldThrow<IllegalStateException> {
            store.settled(secondHandle)
        }
    }
})
