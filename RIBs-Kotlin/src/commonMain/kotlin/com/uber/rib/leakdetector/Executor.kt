package com.uber.rib

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Executor for delayed logic execution.
 */
object Executor {

    /**
     * Execute the given logic after the given delay assuming the given maximum frame duration.
     *
     * This allows excluding the time elapsed due to breakpoint pauses.
     *
     * Note: The logic closure is not guaranteed to be performed exactly after the given delay. It may be performed
     * later if the actual frame duration exceeds the given maximum frame duration.
     *
     * @param delay The delay to perform the logic in seconds, excluding any potential elapsed time due to breakpoint
     *   pauses.
     * @param maxFrameDuration The maximum duration a single frame should take in milliseconds. Defaults to 33ms.
     * @param logic The closure logic to perform.
     */
    fun execute(delay: Double, maxFrameDuration: Long = 33, logic: () -> Unit) {
        val period = maxFrameDuration / 3
        var lastRunLoopTime = System.currentTimeMillis()
        var properFrameTime = 0.0
        var didExecute = false

        Observable.interval(0, period, TimeUnit.MILLISECONDS, Schedulers.computation())
            .takeWhile { !didExecute }
            .subscribe { _ ->
                val currentTime = System.currentTimeMillis()
                val trueElapsedTime = (currentTime - lastRunLoopTime) / 1000.0
                lastRunLoopTime = currentTime

                // If we did drop frame, we under-count the frame duration, which is fine. It
                // just means the logic is performed slightly later.
                val boundedElapsedTime = minOf(trueElapsedTime, maxFrameDuration / 1000.0)
                properFrameTime += boundedElapsedTime

                if (properFrameTime > delay) {
                    didExecute = true
                    logic()
                }
            }
    }
}
