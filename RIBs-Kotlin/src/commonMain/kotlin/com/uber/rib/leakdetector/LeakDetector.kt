/*
 * Copyright (C) 2017. Uber Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.rib

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Leak detection status.
 */
enum class LeakDetectionStatus {
    /** Leak detection is in progress. */
    IN_PROGRESS,

    /** Leak detection has completed. */
    DID_COMPLETE
}

/**
 * The default time values used for leak detection expectations.
 */
object LeakDefaultExpectationTime {
    /** The object deallocation time in seconds. */
    const val DEALLOCATION = 1.0

    /** The view disappear time in seconds. */
    const val VIEW_DISAPPEAR = 5.0
}

/**
 * The handle for a scheduled leak detection.
 */
interface LeakDetectionHandle {
    /**
     * Cancel the scheduled detection.
     */
    fun cancel()
}

/**
 * An expectation based leak detector, that allows an object's owner to set an expectation that an owned object to be
 * deallocated within a time frame.
 *
 * A [Router] that owns an [Interactor] might for example expect its [Interactor] be deallocated when the [Router]
 * itself is deallocated. If the interactor does not deallocate in time, a runtime assert is triggered, along with
 * critical logging.
 */
object LeakDetector {

    private val trackingObjects = ConcurrentHashMap<String, WeakReference<Any>>()
    private val expectationCount = BehaviorSubject.createDefault(0)
    private var disableLeakDetectorValue: Boolean? = null

    /**
     * The status of leak detection.
     *
     * The status changes between IN_PROGRESS and DID_COMPLETE as units register for new detections, cancel existing
     * detections, and existing detections complete.
     */
    val status: Observable<LeakDetectionStatus>
        get() = expectationCount
            .map { count ->
                if (count > 0) LeakDetectionStatus.IN_PROGRESS else LeakDetectionStatus.DID_COMPLETE
            }
            .distinctUntilChanged()

    private val disableLeakDetector: Boolean
        get() {
            if (disableLeakDetectorValue == null) {
                val envValue = System.getenv("DISABLE_LEAK_DETECTION")?.lowercase()
                disableLeakDetectorValue = envValue == "yes" || envValue == "true"
            }
            return disableLeakDetectorValue ?: false
        }

    /**
     * Sets up an expectation for the given object to be deallocated within the given time.
     *
     * @param obj The object to track for deallocation.
     * @param inTime The time the given object is expected to be deallocated within.
     * @return The handle that can be used to cancel the expectation.
     */
    fun expectDeallocate(obj: Any, inTime: Double = LeakDefaultExpectationTime.DEALLOCATION): LeakDetectionHandle {
        expectationCount.onNext(expectationCount.value!! + 1)

        val objectDescription = obj.toString()
        val objectId = System.identityHashCode(obj).toString()
        trackingObjects[objectId] = WeakReference(obj)

        val handle = LeakDetectionHandleImpl {
            expectationCount.onNext(expectationCount.value!! - 1)
        }

        Executor.execute(inTime) {
            // Check for cancelled status
            if (!handle.cancelled) {
                val weakRef = trackingObjects[objectId]
                val didDeallocate = weakRef?.get() == null
                val message = "<$objectDescription: $objectId> has leaked. Objects are expected to be deallocated at this time: $trackingObjects"

                if (disableLeakDetector) {
                    if (!didDeallocate) {
                        println("Leak detection is disabled. This should only be used for debugging purposes.")
                        println(message)
                    }
                } else {
                    check(didDeallocate) { message }
                }
            }

            expectationCount.onNext(expectationCount.value!! - 1)
        }

        return handle
    }

    /**
     * Sets up an expectation for the given view controller to disappear within the given time.
     *
     * @param viewComponent The view component expected to disappear.
     * @param inTime The time the given view controller is expected to disappear.
     * @return The handle that can be used to cancel the expectation.
     */
    fun expectViewControllerDisappear(
        viewComponent: Any,
        inTime: Double = LeakDefaultExpectationTime.VIEW_DISAPPEAR
    ): LeakDetectionHandle {
        expectationCount.onNext(expectationCount.value!! + 1)

        val handle = LeakDetectionHandleImpl {
            expectationCount.onNext(expectationCount.value!! - 1)
        }

        val weakViewComponent = WeakReference(viewComponent)

        Executor.execute(inTime) {
            // Check for cancelled status
            val component = weakViewComponent.get()
            if (component != null && !handle.cancelled) {
                // Platform-specific view disappear check would go here
                // For now, we just check if it's been garbage collected
                val viewDidDisappear = weakViewComponent.get() == null
                val message = "$component appearance has leaked. Either its parent router who does not own a view controller was detached, but failed to dismiss the leaked view controller; or the view controller is reused and re-added to window, yet the router is not re-attached but re-created. Objects are expected to be deallocated at this time: $trackingObjects"

                if (disableLeakDetector) {
                    if (!viewDidDisappear) {
                        println("Leak detection is disabled. This should only be used for debugging purposes.")
                        println(message)
                    }
                } else {
                    check(viewDidDisappear) { message }
                }
            }

            expectationCount.onNext(expectationCount.value!! - 1)
        }

        return handle
    }

    /**
     * Reset the state of Leak Detector, internal for testing only.
     */
    internal fun reset() {
        trackingObjects.clear()
        expectationCount.onNext(0)
    }
}

private class LeakDetectionHandleImpl(
    private val cancelClosure: (() -> Unit)? = null
) : LeakDetectionHandle {

    private val cancelledSubject = BehaviorSubject.createDefault(false)

    val cancelled: Boolean
        get() = cancelledSubject.value ?: false

    override fun cancel() {
        cancelledSubject.onNext(true)
        cancelClosure?.invoke()
    }
}
