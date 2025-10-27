package com.uber.rib

/**
 * Base class of an [Interactor] that actually has an associated presenter and view.
 */
open class PresentableInteractor<PresenterType>(
    /**
     * The presenter associated with this [Interactor].
     *
     * Note: This holds a strong reference to the given presenter.
     */
    val presenter: PresenterType
) : Interactor() {

    protected fun finalize() {
        LeakDetector.expectDeallocate(presenter as Any)
        super.finalize()
    }
}
