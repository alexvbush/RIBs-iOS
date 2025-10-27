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

package com.uber.rib.extensions

/**
 * MutableList extensions.
 */

/**
 * Remove the given element from this list, by comparing object references.
 *
 * @param element The element to remove.
 * @return true if the element was removed, false otherwise.
 */
fun <T> MutableList<T>.removeByReference(element: T): Boolean {
    val index = indexOfFirst { it === element }
    if (index >= 0) {
        removeAt(index)
        return true
    }
    return false
}
