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

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * The lifecycle stages of a router scope.
 */
enum class RouterLifecycle {
    /** Router did load. */
    DID_LOAD
}

/**
 * The scope of a [Router], defining various lifecycles of a [Router].
 */
interface RouterScope {
    /**
     * An observable that emits values when the router scope reaches its corresponding life-cycle stages.
     * This observable completes when the router scope is deallocated.
     */
    val lifecycle: Observable<RouterLifecycle>
}

/**
 * The base protocol for all routers.
 */
interface Routing : RouterScope {
    /**
     * The base interactable associated with this [Router].
     */
    val interactable: Interactable

    /**
     * The list of children routers of this [Router].
     */
    val children: List<Routing>

    /**
     * Loads the [Router].
     *
     * Note: This method is internally used by the framework. Application code should never
     * invoke this method explicitly.
     */
    fun load()

    /**
     * Attaches the given router as a child.
     *
     * @param child The child router to attach.
     */
    fun attachChild(child: Routing)

    /**
     * Detaches the given router from the tree.
     *
     * @param child The child router to detach.
     */
    fun detachChild(child: Routing)
}

/**
 * The base class of all routers that does not own view controllers, representing application states.
 *
 * A router acts on inputs from its corresponding interactor, to manipulate application state, forming a tree of
 * routers. A router may obtain a view controller through constructor injection to manipulate view controller tree.
 * The DI structure guarantees that the injected view controller must be from one of this router's ancestors.
 * Router drives the lifecycle of its owned [Interactor].
 *
 * Routers should always use helper builders to instantiate children routers.
 */
open class Router<InteractorType>(
    /**
     * The corresponding [Interactor] owned by this [Router].
     */
    val interactor: InteractorType
) : Routing where InteractorType : Interactable {

    /**
     * The base [Interactable] associated with this [Router].
     */
    override val interactable: Interactable = interactor

    /**
     * The list of children [Router]s of this [Router].
     */
    private val _children = mutableListOf<Routing>()
    override val children: List<Routing>
        get() = _children

    /**
     * The observable that emits values when the router scope reaches its corresponding life-cycle stages.
     *
     * This observable completes when the router scope is deallocated.
     */
    private val lifecycleSubject = PublishSubject.create<RouterLifecycle>()
    override val lifecycle: Observable<RouterLifecycle>
        get() = lifecycleSubject

    internal val deinitDisposable = CompositeDisposable()
    private var didLoadFlag = false

    /**
     * Loads the [Router].
     *
     * Note: This method is internally used by the framework. Application code should never invoke this method
     * explicitly.
     */
    final override fun load() {
        if (didLoadFlag) {
            return
        }

        didLoadFlag = true
        internalDidLoad()
        didLoad()
    }

    /**
     * Called when the router has finished loading.
     *
     * This method is invoked only once. Subclasses should override this method to perform one time setup logic,
     * such as attaching immutable children. The default implementation does nothing.
     */
    protected open fun didLoad() {
        // No-op
    }

    /**
     * Attaches the given router as a child.
     *
     * @param child The child [Router] to attach.
     */
    final override fun attachChild(child: Routing) {
        check(!_children.any { it === child }) {
            "Attempt to attach child: $child, which is already attached to $this."
        }

        _children.add(child)

        // Activate child first before loading. Router usually attaches immutable children in didLoad.
        // We need to make sure the RIB is activated before letting it attach immutable children.
        child.interactable.activate()
        child.load()
    }

    /**
     * Detaches the given [Router] from the tree.
     *
     * @param child The child [Router] to detach.
     */
    final override fun detachChild(child: Routing) {
        child.interactable.deactivate()
        _children.removeAll { it === child }
    }

    internal open fun internalDidLoad() {
        bindSubtreeActiveState()
        lifecycleSubject.onNext(RouterLifecycle.DID_LOAD)
    }

    private fun bindSubtreeActiveState() {
        val disposable = interactable.isActiveStream
            // Do not retain self here to guarantee execution. Using weak reference equivalent.
            .subscribe { isActive ->
                // When interactor becomes active, we are attached to parent, otherwise we are detached.
                setSubtreeActive(isActive)
            }
        deinitDisposable.add(disposable)
    }

    private fun setSubtreeActive(active: Boolean) {
        if (active) {
            iterateSubtree(this) { router ->
                if (!router.interactable.isActive) {
                    router.interactable.activate()
                }
            }
        } else {
            iterateSubtree(this) { router ->
                if (router.interactable.isActive) {
                    router.interactable.deactivate()
                }
            }
        }
    }

    private fun iterateSubtree(root: Routing, closure: (Routing) -> Unit) {
        closure(root)
        for (child in root.children) {
            iterateSubtree(child, closure)
        }
    }

    private fun detachAllChildren() {
        for (child in children) {
            detachChild(child)
        }
    }

    protected fun finalize() {
        interactable.deactivate()

        if (children.isNotEmpty()) {
            detachAllChildren()
        }

        lifecycleSubject.onComplete()
        deinitDisposable.dispose()

        LeakDetector.expectDeallocate(interactable)
    }
}
