package com.uber.rib

/**
 * The base protocol for all routers that own their own view controllers.
 */
interface ViewableRouting : Routing {
    /**
     * The base view controllable associated with this [Router].
     */
    val viewControllable: ViewControllable
}

/**
 * The base class of all routers that owns view controllers, representing application states.
 *
 * A [Router] acts on inputs from its corresponding interactor, to manipulate application state and view state,
 * forming a tree of routers that drives the tree of view controllers. Router drives the lifecycle of its owned
 * interactor. [Router]s should always use helper builders to instantiate children [Router]s.
 */
open class ViewableRouter<InteractorType, ViewControllerType>(
    interactor: InteractorType,
    /**
     * The corresponding view controller owned by this [Router].
     */
    val viewController: ViewControllerType
) : Router<InteractorType>(interactor), ViewableRouting where InteractorType : Interactable {

    /**
     * The base [ViewControllable] associated with this [Router].
     */
    override val viewControllable: ViewControllable

    init {
        require(viewController is ViewControllable) {
            "$viewController should implement ViewControllable"
        }
        this.viewControllable = viewController as ViewControllable
    }

    override fun internalDidLoad() {
        setupViewControllerLeakDetection()
        super.internalDidLoad()
    }

    private var viewControllerDisappearExpectation: LeakDetectionHandle? = null

    private fun setupViewControllerLeakDetection() {
        val disposable = interactable.isActiveStream
            .subscribe { isActive ->
                viewControllerDisappearExpectation?.cancel()
                viewControllerDisappearExpectation = null

                if (!isActive) {
                    viewControllerDisappearExpectation =
                        LeakDetector.expectViewControllerDisappear(viewControllable.viewComponent)
                }
            }
        deinitDisposable.add(disposable)
    }

    protected fun finalize() {
        LeakDetector.expectDeallocate(
            viewControllable.viewComponent,
            inTime = LeakDefaultExpectationTime.VIEW_DISAPPEAR
        )
        super.finalize()
    }
}
