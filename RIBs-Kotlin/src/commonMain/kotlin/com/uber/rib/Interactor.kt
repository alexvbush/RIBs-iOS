package com.uber.rib

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject

/**
 * Protocol defining the activeness of an interactor's scope.
 */
interface InteractorScope {
    /**
     * Indicates if the interactor is active.
     */
    val isActive: Boolean

    /**
     * The lifecycle of this interactor.
     *
     * Note: Subscription to this stream always immediately returns the last event. This stream terminates after
     * the interactor is deallocated.
     */
    val isActiveStream: Observable<Boolean>
}

/**
 * The base protocol for all interactors.
 */
interface Interactable : InteractorScope {
    /**
     * Activate this interactor.
     *
     * Note: This method is internally invoked by the corresponding router. Application code should never explicitly
     * invoke this method.
     */
    fun activate()

    /**
     * Deactivate this interactor.
     *
     * Note: This method is internally invoked by the corresponding router. Application code should never explicitly
     * invoke this method.
     */
    fun deactivate()
}

/**
 * An [Interactor] defines a unit of business logic that corresponds to a router unit.
 *
 * An [Interactor] has a lifecycle driven by its owner router. When the corresponding router is attached to its
 * parent, its interactor becomes active. And when the router is detached from its parent, its [Interactor] resigns
 * active.
 *
 * An [Interactor] should only perform its business logic when it's currently active.
 */
open class Interactor : Interactable {

    private val isActiveSubject = BehaviorSubject.createDefault(false)
    internal var activenessDisposable: CompositeDisposable? = null

    /**
     * Indicates if the interactor is active.
     */
    override val isActive: Boolean
        get() = isActiveSubject.value ?: false

    /**
     * A stream notifying on the lifecycle of this interactor.
     */
    override val isActiveStream: Observable<Boolean>
        get() = isActiveSubject.distinctUntilChanged()

    /**
     * Activate the [Interactor].
     *
     * Note: This method is internally invoked by the corresponding router. Application code should never explicitly
     * invoke this method.
     */
    final override fun activate() {
        if (isActive) {
            return
        }

        activenessDisposable = CompositeDisposable()
        isActiveSubject.onNext(true)
        didBecomeActive()
    }

    /**
     * The interactor did become active.
     *
     * Note: This method is driven by the attachment of this interactor's owner router. Subclasses should override
     * this method to setup subscriptions and initial states.
     */
    protected open fun didBecomeActive() {
        // No-op
    }

    /**
     * Deactivate this [Interactor].
     *
     * Note: This method is internally invoked by the corresponding router. Application code should never explicitly
     * invoke this method.
     */
    final override fun deactivate() {
        if (!isActive) {
            return
        }

        willResignActive()

        activenessDisposable?.dispose()
        activenessDisposable = null

        isActiveSubject.onNext(false)
    }

    /**
     * Called when the [Interactor] will resign the active state.
     *
     * This method is driven by the detachment of this interactor's owner router. Subclasses should override this
     * method to cleanup any resources and states of the [Interactor]. The default implementation does nothing.
     */
    protected open fun willResignActive() {
        // No-op
    }

    protected fun finalize() {
        if (isActive) {
            deactivate()
        }
        isActiveSubject.onComplete()
    }
}

/**
 * Interactor related [Observable] extensions.
 */
/**
 * Confines the observable's subscription to the given interactor scope. The subscription is only triggered
 * after the interactor scope is active and before the interactor scope resigns active. This composition
 * delays the subscription but does not dispose the subscription, when the interactor scope becomes inactive.
 *
 * Note: This method should only be used for subscriptions outside of an [Interactor], for cases where a
 * piece of logic is only executed when the bound interactor scope is active.
 *
 * Note: Only the latest value from this observable is emitted. Values emitted when the interactor is not
 * active, are ignored.
 *
 * @param interactorScope The interactor scope whose activeness this observable is confined to.
 * @return The [Observable] confined to this interactor's activeness lifecycle.
 */
fun <T> Observable<T>.confineTo(interactorScope: InteractorScope): Observable<T> {
    return Observable.combineLatest(
        interactorScope.isActiveStream,
        this
    ) { isActive, value -> Pair(isActive, value) }
        .filter { (isActive, _) -> isActive }
        .map { (_, value) -> value }
}

/**
 * Interactor related [Disposable] extensions.
 */
/**
 * Disposes the subscription based on the lifecycle of the given [Interactor]. The subscription is disposed
 * when the interactor is deactivated.
 *
 * Note: This is the preferred method when trying to confine a subscription to the lifecycle of an
 * [Interactor].
 *
 * When using this composition, the subscription closure may freely retain the interactor itself, since the
 * subscription closure is disposed once the interactor is deactivated, thus releasing the retain cycle before
 * the interactor needs to be deallocated.
 *
 * If the given interactor is inactive at the time this method is invoked, the subscription is immediately
 * terminated.
 *
 * @param interactor The interactor to dispose the subscription based on.
 * @return The disposable.
 */
fun Disposable.disposeOnDeactivate(interactor: Interactor): Disposable {
    val activenessDisposable = interactor.activenessDisposable
    if (activenessDisposable != null) {
        activenessDisposable.add(this)
    } else {
        dispose()
        println("Subscription immediately terminated, since $interactor is inactive.")
    }
    return this
}
