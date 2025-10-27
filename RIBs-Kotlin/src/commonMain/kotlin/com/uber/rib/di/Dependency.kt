package com.uber.rib.di

/**
 * The base dependency protocol.
 *
 * Subclasses should define a set of properties that are required by the module from the DI graph. A dependency is
 * typically provided and satisfied by its immediate parent module.
 */
interface Dependency

/**
 * The special empty dependency.
 */
interface EmptyDependency : Dependency
