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
