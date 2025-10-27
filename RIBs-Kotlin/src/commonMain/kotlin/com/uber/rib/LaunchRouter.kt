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
