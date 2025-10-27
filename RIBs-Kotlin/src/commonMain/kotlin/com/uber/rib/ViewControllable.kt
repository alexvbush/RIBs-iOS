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
