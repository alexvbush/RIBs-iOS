# RIBs - Kotlin Multiplatform Implementation

This is a Kotlin Multiplatform (KMP) port of Uber's RIBs architecture framework, originally implemented for iOS in Swift.

## Overview

RIBs is a cross-platform architecture framework that aims to make mobile applications:
- **Testable**: Clear separation of business logic from view logic
- **Scalable**: Built to handle large teams and codebases
- **Maintainable**: Consistent patterns across the codebase
- **Safe**: Strong typing and compile-time guarantees

The RIBs architecture is based on the **Router, Interactor, Builder** pattern, with optional Presenter and View components.

## Architecture Components

### Core Components

- **Router**: Manages navigation and child RIB lifecycle
- **Interactor**: Contains business logic, driven by router lifecycle
- **Builder**: Constructs and wires up RIB components
- **Presenter** (optional): Translates business models to view models
- **View** (optional): Platform-specific UI implementation

### Supporting Components

- **Component**: Dependency injection container
- **Worker**: Self-contained logic units with their own lifecycle
- **Workflow**: Sequential step-based flows through the RIB tree

## Platform Support

This Kotlin Multiplatform implementation supports:

- **Android** (androidTarget)
- **iOS** (iosX64, iosArm64, iosSimulatorArm64)
- **JVM** (for desktop/server applications)

## Key Differences from Swift Implementation

### 1. Platform-Agnostic View Abstraction

Unlike the iOS-specific `UIViewController`, this implementation uses a generic `ViewControllable` interface:

```kotlin
interface ViewControllable {
    val viewComponent: Any  // Platform-specific: UIViewController, Activity, Fragment, View, etc.
}
```

Each platform can implement this differently:
- **iOS**: UIViewController
- **Android**: Activity, Fragment, or View
- **Web**: DOM element

### 2. Reactive Framework

Uses RxJava3 instead of RxSwift for cross-platform reactive programming.

### 3. Thread Safety

Uses Kotlin's coroutines and Mutex instead of NSRecursiveLock for thread-safe operations.

## Getting Started

### Gradle Setup

Add the dependency to your project:

```kotlin
dependencies {
    implementation("com.uber.rib:RIBs-Kotlin:0.16.3")
}
```

### Creating a RIB

#### 1. Define Dependency and Component

```kotlin
// Dependencies required from parent
interface MyDependency : Dependency {
    val someService: SomeService
}

// Component providing dependencies to children
class MyComponent(
    dependency: MyDependency
) : Component<MyDependency>(dependency) {

    // Shared instances are lazily created and cached
    fun mySharedService(): MySharedService = shared {
        MySharedService(dependency.someService)
    }
}
```

#### 2. Create Interactor

```kotlin
class MyInteractor : Interactor() {

    override fun didBecomeActive() {
        super.didBecomeActive()
        // Setup subscriptions and initial state
        println("MyInteractor became active")
    }

    override fun willResignActive() {
        // Cleanup resources
        println("MyInteractor will resign active")
        super.willResignActive()
    }
}
```

#### 3. Create Router

```kotlin
class MyRouter(
    interactor: MyInteractor
) : Router<MyInteractor>(interactor) {

    override fun didLoad() {
        super.didLoad()
        // Attach any immutable children
    }
}
```

#### 4. Create Builder

```kotlin
class MyBuilder(
    dependency: MyDependency
) : Builder<MyDependency>(dependency) {

    fun build(): MyRouter {
        val component = MyComponent(dependency)
        val interactor = MyInteractor()
        val router = MyRouter(interactor)
        return router
    }
}
```

### Lifecycle Management

RIBs automatically manage component lifecycle:

```kotlin
// Attach a child RIB
router.attachChild(childRouter)  // Activates child interactor and calls load()

// Detach a child RIB
router.detachChild(childRouter)  // Deactivates child interactor
```

### Reactive Extensions

Confine observables to interactor lifecycle:

```kotlin
someObservable
    .confineTo(interactorScope)
    .subscribe { value ->
        // Only receives values when interactor is active
    }
    .disposeOnDeactivate(interactor)
```

### Using Workers

Workers are self-contained logic units:

```kotlin
class MyWorker : Worker() {
    override fun didStart(interactorScope: InteractorScope) {
        // Worker logic starts here
        println("Worker started")
    }

    override fun didStop() {
        // Cleanup
        println("Worker stopped")
    }
}

// In your interactor:
val worker = MyWorker()
worker.start(this)  // Starts when interactor is active
// Worker automatically stops when interactor deactivates
```

### Using Workflows

Workflows enable sequential multi-step flows:

```kotlin
class LoginWorkflow : Workflow<RootInteractor>() {

    fun login(username: String, password: String): Observable<User> {
        return onStep { rootInteractor ->
            // Navigate to login screen
            Observable.just(Pair(loginInteractor, Unit))
        }
        .onStep { loginInteractor, _ ->
            // Perform login
            loginInteractor.login(username, password)
                .map { user -> Pair(rootInteractor, user) }
        }
        .onStep { rootInteractor, user ->
            // Navigate to home screen
            Observable.just(Pair(homeInteractor, user))
        }
        .commit()
        .asObservable()
        .map { (_, user) -> user }
    }
}
```

## Memory Leak Detection

Built-in leak detection helps catch memory issues:

```kotlin
// Automatically enabled in debug builds
// Disable via environment variable: DISABLE_LEAK_DETECTION=true

// Manual leak detection:
LeakDetector.expectDeallocate(someObject, inTime = 1.0)
```

## Testing

RIBs are designed to be highly testable:

```kotlin
class MyInteractorTest {
    private lateinit var interactor: MyInteractor

    @Before
    fun setup() {
        interactor = MyInteractor()
    }

    @Test
    fun `test activation`() {
        interactor.activate()
        assertTrue(interactor.isActive)

        interactor.deactivate()
        assertFalse(interactor.isActive)
    }
}
```

## Comparison with Original iOS Implementation

| Feature | iOS (Swift) | Kotlin Multiplatform |
|---------|-------------|---------------------|
| Reactive Framework | RxSwift | RxJava3 |
| View Abstraction | UIViewController | ViewControllable (platform-agnostic) |
| Thread Safety | NSRecursiveLock | Kotlin Mutex |
| Platforms | iOS only | Android, iOS, JVM |
| ComponentizedBuilder | ✅ | ❌ (Basic Builder only) |
| MultiStageBuilder | ✅ | ❌ |

## Migration from Swift

If you're familiar with the Swift implementation:

1. **Protocols → Interfaces**: Swift protocols map to Kotlin interfaces
2. **Optional types**: Swift optionals (`Type?`) map to Kotlin nullables (`Type?`)
3. **Extensions**: Swift extensions work similarly in Kotlin
4. **Generics**: Swift generics map directly to Kotlin generics
5. **Weak references**: Swift `weak` maps to Kotlin `WeakReference`

## Architecture Diagram

```
┌─────────────────────────────────────┐
│         Application Root            │
│         (LaunchRouter)              │
└─────────────────┬───────────────────┘
                  │
        ┌─────────┴──────────┐
        │                    │
   ┌────▼─────┐        ┌────▼─────┐
   │  Router  │        │  Router  │
   │  Child1  │        │  Child2  │
   └────┬─────┘        └────┬─────┘
        │                   │
   ┌────▼─────┐        ┌───▼──────┐
   │Interactor│        │Interactor│
   │  (Logic) │        │  (Logic) │
   └────┬─────┘        └────┬─────┘
        │                   │
   ┌────▼─────┐        ┌───▼──────┐
   │Presenter │        │Presenter │
   └────┬─────┘        └────┬─────┘
        │                   │
   ┌────▼─────┐        ┌───▼──────┐
   │   View   │        │   View   │
   └──────────┘        └──────────┘
```

## Contributing

Contributions are welcome! Please ensure:
- All tests pass
- Code follows Kotlin conventions
- Platform compatibility is maintained

## License

```
Copyright (C) 2017 Uber Technologies

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Resources

- [Original RIBs GitHub](https://github.com/uber/RIBs)
- [RIBs Wiki](https://github.com/uber/RIBs/wiki)
- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
