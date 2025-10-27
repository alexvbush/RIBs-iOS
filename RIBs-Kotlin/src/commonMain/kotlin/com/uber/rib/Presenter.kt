package com.uber.rib

/**
 * The base presenter protocol.
 */
interface Presentable

/**
 * The base class of all presenters.
 *
 * Presenters translate business models into view models and formats the data to be consumed by views.
 */
open class Presenter<ViewControllerType>(
    /**
     * The corresponding view controller.
     */
    val viewController: ViewControllerType
) : Presentable
