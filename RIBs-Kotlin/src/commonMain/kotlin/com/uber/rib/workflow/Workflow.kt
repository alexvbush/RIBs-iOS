package com.uber.rib.workflow

import com.uber.rib.Interactable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * Defines the base class for a sequence of steps that execute a flow through the application RIB tree.
 *
 * At each step of a [Workflow] is a pair of value and actionable item. The value can be used to make logic decisions.
 * The actionable item is invoked to perform logic for the step. Typically the actionable item is the [Interactor] of a
 * RIB.
 *
 * A workflow should always start at the root of the tree.
 */
open class Workflow<ActionableItemType> {

    private val subject = PublishSubject.create<Pair<ActionableItemType, Unit>>()
    private var didInvokeComplete = false

    /**
     * The composite disposable that contains all subscriptions including the original workflow
     * as well as all the forked ones.
     */
    internal val compositeDisposable = CompositeDisposable()

    /**
     * Called when the last step observable is completed.
     *
     * Subclasses should override this method if they want to execute logic at this point in the [Workflow] lifecycle.
     * The default implementation does nothing.
     */
    protected open fun didComplete() {
        // No-op
    }

    /**
     * Called when the [Workflow] is forked.
     *
     * Subclasses should override this method if they want to execute logic at this point in the [Workflow] lifecycle.
     * The default implementation does nothing.
     */
    protected open fun didFork() {
        // No-op
    }

    /**
     * Called when the last step observable has error.
     *
     * Subclasses should override this method if they want to execute logic at this point in the [Workflow] lifecycle.
     * The default implementation does nothing.
     */
    protected open fun didReceiveError(error: Throwable) {
        // No-op
    }

    /**
     * Execute the given closure as the root step.
     *
     * @param onStep The closure to execute for the root step.
     * @return The next step.
     */
    fun <NextActionableItemType, NextValueType> onStep(
        onStep: (ActionableItemType) -> Observable<Pair<NextActionableItemType, NextValueType>>
    ): Step<ActionableItemType, NextActionableItemType, NextValueType> {
        return Step(this, subject.take(1))
            .onStep { actionableItem, _ ->
                onStep(actionableItem)
            }
    }

    /**
     * Subscribe and start the [Workflow] sequence.
     *
     * @param actionableItem The initial actionable item for the first step.
     * @return The disposable of this workflow.
     */
    fun subscribe(actionableItem: ActionableItemType): Disposable {
        check(compositeDisposable.size() > 0) {
            "Attempt to subscribe to $this before it is committed."
        }

        subject.onNext(Pair(actionableItem, Unit))
        return compositeDisposable
    }

    internal fun didCompleteIfNotYet() {
        // Since a workflow may be forked to produce multiple subscribed Rx chains, we should
        // ensure the didComplete method is only invoked once per Workflow instance. See `Step.commit`
        // on why the side-effects must be added at the end of the Rx chains.
        if (didInvokeComplete) {
            return
        }
        didInvokeComplete = true
        didComplete()
    }

    internal fun invokeDidFork() {
        didFork()
    }
}

/**
 * Defines a single step in a [Workflow].
 *
 * A step may produce a next step with a new value and actionable item, eventually forming a sequence of [Workflow]
 * steps.
 *
 * Steps are asynchronous by nature.
 */
class Step<WorkflowActionableItemType, ActionableItemType, ValueType> internal constructor(
    private val workflow: Workflow<WorkflowActionableItemType>,
    private var observable: Observable<Pair<ActionableItemType, ValueType>>
) {

    /**
     * Executes the given closure for this step.
     *
     * @param onStep The closure to execute for the [Step].
     * @return The next step.
     */
    fun <NextActionableItemType, NextValueType> onStep(
        onStep: (ActionableItemType, ValueType) -> Observable<Pair<NextActionableItemType, NextValueType>>
    ): Step<WorkflowActionableItemType, NextActionableItemType, NextValueType> {
        val confinedNextStep = observable
            .flatMap { (actionableItem, value) ->
                // Check if actionable item is an Interactable to confine to its lifecycle
                if (actionableItem is Interactable) {
                    actionableItem.isActiveStream
                        .map { isActive -> Triple(isActive, actionableItem, value) }
                } else {
                    Observable.just(Triple(true, actionableItem, value))
                }
            }
            .filter { (isActive, _, _) -> isActive }
            .take(1)
            .flatMap { (_, actionableItem, value) ->
                onStep(actionableItem, value)
            }
            .take(1)
            .share()

        return Step(workflow, confinedNextStep)
    }

    /**
     * Executes the given closure when the [Step] produces an error.
     *
     * @param onError The closure to execute when an error occurs.
     * @return This step.
     */
    fun onError(onError: (Throwable) -> Unit): Step<WorkflowActionableItemType, ActionableItemType, ValueType> {
        observable = observable.doOnError(onError)
        return this
    }

    /**
     * Commit the steps of the [Workflow] sequence.
     *
     * @return The committed [Workflow].
     */
    fun commit(): Workflow<WorkflowActionableItemType> {
        // Side-effects must be chained at the last observable sequence, since errors and complete
        // events can be emitted by any observables on any steps of the workflow.
        val disposable = observable
            .doOnError { error -> workflow.didReceiveError(error) }
            .doOnComplete { workflow.didCompleteIfNotYet() }
            .subscribe()
        workflow.compositeDisposable.add(disposable)
        return workflow
    }

    /**
     * Convert the [Workflow] into an observable.
     *
     * @return The observable representation of this [Workflow].
     */
    fun asObservable(): Observable<Pair<ActionableItemType, ValueType>> {
        return observable
    }
}

/**
 * [Workflow] related observable extensions.
 */
/**
 * Fork the step from this observable.
 *
 * @param workflow The workflow this step belongs to.
 * @return The newly forked step in the workflow.
 */
fun <WorkflowActionableItemType, ActionableItemType, ValueType> Observable<Pair<ActionableItemType, ValueType>>.fork(
    workflow: Workflow<WorkflowActionableItemType>
): Step<WorkflowActionableItemType, ActionableItemType, ValueType> {
    workflow.invokeDidFork()
    return Step(workflow, this)
}

/**
 * [Workflow] related [Disposable] extensions.
 */
/**
 * Dispose the subscription when the given [Workflow] is disposed.
 *
 * When using this composition, the subscription closure may freely retain the workflow itself, since the
 * subscription closure is disposed once the workflow is disposed, thus releasing the retain cycle before the
 * [Workflow] needs to be deallocated.
 *
 * Note: This is the preferred method when trying to confine a subscription to the lifecycle of a [Workflow].
 *
 * @param workflow The workflow to dispose the subscription with.
 */
fun <ActionableItemType> Disposable.disposeWith(workflow: Workflow<ActionableItemType>) {
    workflow.compositeDisposable.add(this)
}
