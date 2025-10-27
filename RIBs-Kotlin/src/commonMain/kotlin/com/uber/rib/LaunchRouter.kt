package com.uber.rib

/**
 * The root [Router] of an application.
 */
interface LaunchRouting : ViewableRouting {
    /**
     * Launches the router tree.
     *
     * @param window The application window/root container to launch from.
     */
    fun launch(window: Any)
}

/**
 * The application root router base class, that acts as the root of the router tree.
 *
 * Note: The window parameter in [launch] is platform-specific:
 * - iOS: UIWindow
 * - Android: Activity or ViewGroup
 * - Web: DOM element
 */
open class LaunchRouter<InteractorType, ViewControllerType>(
    interactor: InteractorType,
    viewController: ViewControllerType
) : ViewableRouter<InteractorType, ViewControllerType>(interactor, viewController), LaunchRouting
    where InteractorType : Interactable {

    /**
     * Launches the router tree.
     *
     * Platform implementations should override this method to perform platform-specific
     * setup (e.g., setting the root view controller on iOS, or setting content view on Android).
     *
     * @param window The window/root container to launch the router tree in.
     */
    override fun launch(window: Any) {
        // Platform-specific implementation should be provided by subclasses
        // Common behavior:
        interactable.activate()
        load()
    }
}
