package com.uber.rib.di

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking

/**
 * The base class for all components.
 *
 * A component defines private properties a RIB provides to its internal [Router], [Interactor], [Presenter] and
 * view units, as well as public properties to its child RIBs.
 *
 * A component subclass implementation should conform to child 'Dependency' protocols, defined by all of its immediate
 * children.
 */
open class Component<DependencyType>(
    /**
     * The dependency of this [Component].
     */
    val dependency: DependencyType
) : Dependency {

    private val sharedInstances = mutableMapOf<String, Any>()
    private val lock = Mutex()

    /**
     * Used to create a shared dependency in your [Component] subclass. Shared dependencies are retained and reused
     * by the component. Each dependent asking for this dependency will receive the same instance while the component
     * is alive.
     *
     * Note: Any shared dependency's constructor may not switch threads as this might cause issues.
     *
     * @param key Unique identifier for the shared instance (automatically provided by calling function name)
     * @param factory The closure to construct the dependency.
     * @return The instance.
     */
    protected fun <T> shared(key: String = Thread.currentThread().stackTrace[2].methodName, factory: () -> T): T {
        return runBlocking {
            lock.withLock {
                @Suppress("UNCHECKED_CAST")
                val instance = sharedInstances[key] as? T
                if (instance != null) {
                    return@withLock instance
                }

                val newInstance = factory()
                sharedInstances[key] = newInstance as Any
                newInstance
            }
        }
    }
}

/**
 * The special empty component.
 */
open class EmptyComponent : EmptyDependency
