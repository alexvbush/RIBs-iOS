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
