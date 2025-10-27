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

package com.uber.rib.worker

import com.uber.rib.InteractorScope
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.lang.ref.WeakReference

/**
 * The base protocol of all workers that perform a self-contained piece of logic.
 *
 * [Worker]s are always bound to an [Interactor]. A [Worker] can only start if its bound [Interactor] is active.
 * It is stopped when its bound interactor is deactivated.
 */
interface Working {
    /**
     * Starts the [Worker].
     *
     * If the bound [InteractorScope] is active, this method starts the [Worker] immediately. Otherwise the [Worker]
     * will start when its bound [Interactor] scope becomes active.
     *
     * @param interactorScope The interactor scope this worker is bound to.
     */
    fun start(interactorScope: InteractorScope)

    /**
     * Stops the worker.
     *
     * Unlike [start], this method always stops the worker immediately.
     */
    fun stop()

    /**
     * Indicates if the worker is currently started.
     */
    val isStarted: Boolean

    /**
     * The lifecycle of this worker.
     *
     * Subscription to this stream always immediately returns the last event. This stream terminates after the
     * [Worker] is deallocated.
     */
    val isStartedStream: Observable<Boolean>
}

/**
 * The base [Worker] implementation.
 */
open class Worker : Working {

    private val isStartedSubject = BehaviorSubject.createDefault(false)
    internal var disposable: CompositeDisposable? = null
    private var interactorBindingDisposable: Disposable? = null

    /**
     * Indicates if the [Worker] is started.
     */
    override val isStarted: Boolean
        get() = isStartedSubject.value ?: false

    /**
     * The lifecycle stream of this [Worker].
     */
    override val isStartedStream: Observable<Boolean>
        get() = isStartedSubject.distinctUntilChanged()

    /**
     * Starts the [Worker].
     *
     * If the bound [InteractorScope] is active, this method starts the [Worker] immediately. Otherwise the [Worker]
     * will start when its bound [Interactor] scope becomes active.
     *
     * @param interactorScope The interactor scope this worker is bound to.
     */
    final override fun start(interactorScope: InteractorScope) {
        if (isStarted) {
            return
        }

        stop()
        isStartedSubject.onNext(true)

        // Create a weak reference wrapper to avoid passing the given scope instance, since usually
        // the given instance is the interactor itself. If the interactor holds onto the worker without
        // de-referencing it when it becomes inactive, there will be a retain cycle.
        val weakInteractorScope = WeakInteractorScope(interactorScope)
        bind(weakInteractorScope)
    }

    /**
     * Called when the the worker has started.
     *
     * Subclasses should override this method and implement any logic that they would want to execute when the [Worker]
     * starts. The default implementation does nothing.
     *
     * @param interactorScope The interactor scope this [Worker] is bound to.
     */
    protected open fun didStart(interactorScope: InteractorScope) {
        // No-op
    }

    /**
     * Stops the worker.
     *
     * Unlike [start], this method always stops the worker immediately.
     */
    final override fun stop() {
        if (!isStarted) {
            return
        }

        isStartedSubject.onNext(false)
        executeStop()
    }

    /**
     * Called when the worker has stopped.
     *
     * Subclasses should override this method and implement any cleanup logic that they might want to execute when
     * the [Worker] stops. The default implementation does nothing.
     *
     * Note: All subscriptions added to the disposable provided in the [didStart] method are automatically disposed
     * when the worker stops.
     */
    protected open fun didStop() {
        // No-op
    }

    private fun bind(interactorScope: InteractorScope) {
        unbindInteractor()

        interactorBindingDisposable = interactorScope.isActiveStream
            .subscribe { isInteractorActive ->
                if (isInteractorActive) {
                    if (isStarted) {
                        executeStart(interactorScope)
                    }
                } else {
                    executeStop()
                }
            }
    }

    private fun executeStart(interactorScope: InteractorScope) {
        disposable = CompositeDisposable()
        didStart(interactorScope)
    }

    private fun executeStop() {
        val currentDisposable = disposable ?: return

        currentDisposable.dispose()
        disposable = null

        didStop()
    }

    private fun unbindInteractor() {
        interactorBindingDisposable?.dispose()
        interactorBindingDisposable = null
    }

    protected fun finalize() {
        stop()
        unbindInteractor()
        isStartedSubject.onComplete()
    }
}

/**
 * Worker related [Disposable] extensions.
 */
/**
 * Disposes the subscription based on the lifecycle of the given [Worker]. The subscription is disposed when the
 * [Worker] is stopped.
 *
 * If the given worker is stopped at the time this method is invoked, the subscription is immediately terminated.
 *
 * Note: When using this composition, the subscription closure may freely retain the [Worker] itself, since the
 * subscription closure is disposed once the [Worker] is stopped, thus releasing the retain cycle before the
 * worker needs to be deallocated.
 *
 * @param worker The [Worker] to dispose the subscription based on.
 * @return The disposable.
 */
fun Disposable.disposeOnStop(worker: Worker): Disposable {
    val compositeDisposable = worker.disposable
    if (compositeDisposable != null) {
        compositeDisposable.add(this)
    } else {
        dispose()
        println("Subscription immediately terminated, since $worker is stopped.")
    }
    return this
}

private class WeakInteractorScope(
    sourceScope: InteractorScope
) : InteractorScope {

    private val sourceScopeRef = WeakReference(sourceScope)

    override val isActive: Boolean
        get() = sourceScopeRef.get()?.isActive ?: false

    override val isActiveStream: Observable<Boolean>
        get() = sourceScopeRef.get()?.isActiveStream ?: Observable.just(false)
}
