package com.uber.rib

/**
 * The base builder protocol that all builders should conform to.
 */
interface Buildable

/**
 * Utility that instantiates a RIB and sets up its internal wirings.
 */
open class Builder<DependencyType>(
    /**
     * The dependency used for this builder to build the RIB.
     */
    val dependency: DependencyType
) : Buildable
