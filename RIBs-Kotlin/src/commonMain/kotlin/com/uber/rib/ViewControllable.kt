package com.uber.rib

/**
 * Basic interface between a [Router] and a platform-specific view component.
 *
 * This abstraction allows the RIBs framework to work across different platforms:
 * - iOS: Implement with UIViewController
 * - Android: Implement with Activity, Fragment, or View
 * - Web: Implement with DOM elements or UI components
 *
 * The framework doesn't dictate the specific view type, allowing each platform
 * to use its native view paradigm.
 */
interface ViewControllable {
    /**
     * Platform-specific view component. The actual type depends on the platform:
     * - iOS: UIViewController
     * - Android: Activity, Fragment, or View
     * - Web: DOM element or UI component
     */
    val viewComponent: Any
}
