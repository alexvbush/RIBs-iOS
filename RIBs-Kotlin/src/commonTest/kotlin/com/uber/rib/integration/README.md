# RIBs Integration Test - ProductPicker

This comprehensive integration test demonstrates a production-quality RIB implementation in Kotlin, modeled after the Swift ClientPicker example from your production codebase.

## Overview

The test implements a complete **ProductPicker** RIB that showcases all the patterns and components used in real-world RIBs applications:

- **Router**: Manages child RIB lifecycle and navigation
- **Interactor**: Contains business logic with async service calls
- **Presenter**: Translates business models to view models
- **Builder**: Constructs the RIB with dependency injection
- **Component**: Provides dependencies to the RIB and its children
- **View**: Platform-agnostic UI abstraction

## Architecture Diagram

```
ProductPickerRIB
├── ProductPickerRouter
│   ├── Manages CreateProductRIB attachment/detachment
│   └── Controls view hierarchy
├── ProductPickerInteractor
│   ├── Fetches products from service
│   ├── Handles search queries
│   ├── Manages product selection
│   └── Tracks analytics events
├── ProductPickerPresenter
│   ├── Converts Product → ProductViewModel
│   └── Delegates user actions to Interactor
├── MockProductPickerView
│   └── Platform-agnostic view implementation
├── ProductPickerComponent
│   ├── Provides ProductService
│   ├── Provides Analytics
│   └── Provides CreateProductBuilder
└── Child: CreateProductRIB
    └── Attached on-demand for product creation
```

## Comparison with Swift ClientPicker

| Component | Swift ClientPicker | Kotlin ProductPicker |
|-----------|-------------------|---------------------|
| **Router** | `ClientPickerRouter` | `ProductPickerRouter` |
| Navigation | `routeToCreateNewClient()` | `routeToCreateProduct()` |
| View | `UIViewController` | `ViewControllable` (platform-agnostic) |
| **Interactor** | `ClientPickerInteractor` | `ProductPickerInteractor` |
| Service | `ClientsService` (async/await) | `ProductService` (RxJava Single) |
| Analytics | `MixpanelWrapper` | `Analytics` (mock) |
| **Presenter** | Implicit in ViewController | `ProductPickerPresenter` |
| **Builder** | `ClientPickerBuilder` | `ProductPickerBuilder` |
| **Component** | `ClientPickerComponent` | `ProductPickerComponent` |
| DI Pattern | Component provides child builders | Same pattern |
| Listener | `ClientPickerListener` | `ProductPickerListener` |

## Test Scenarios

### 1. Full RIB Lifecycle
Tests activation, data loading, and deactivation:
```kotlin
router.load()
interactor.activate()
// Service returns data
// View displays products
interactor.deactivate()
```

### 2. Search Functionality
Tests filtering products by search query:
```kotlin
presenter.searchForProducts("Apple")
// Only "Apple iPhone" and "Apple iPad" shown
```

### 3. Child RIB Routing
Tests attaching and detaching CreateProduct child:
```kotlin
presenter.addNewProduct()
// CreateProductRIB is attached
createProductInteractor.completeWithProduct(newProduct)
// Child is detached, listener notified
```

### 4. Product Selection
Tests selecting a product and notifying listener:
```kotlin
presenter.selectProductWithId("1")
// listener.selectedProduct is set
// Analytics tracks "Select Product"
```

### 5. Empty State
Tests empty state when no products exist:
```kotlin
service.completeWithProducts(emptyList())
// view.isShowingEmptyState == true
```

### 6. Error Handling
Tests error presentation:
```kotlin
service.completeWithError(NetworkError())
// view.displayedError is set
```

## Assumptions & Simplifications

Since some components from your Swift code weren't fully defined, I made the following assumptions:

### 1. Service Layer
**Assumption**: The service returns data asynchronously using RxJava Singles.

```kotlin
interface ProductService {
    fun fetchProducts(userId: String, query: String?): Single<List<Product>>
}
```

**Swift equivalent**: Your `ClientsService` with async/await was converted to RxJava pattern.

### 2. Analytics
**Assumption**: Analytics is a simple event tracker.

```kotlin
interface Analytics {
    fun track(event: String, properties: Map<String, Any>)
}
```

**Swift equivalent**: Your `MixpanelWrapperInterface` was simplified to a basic tracker.

### 3. View Implementation
**Assumption**: The view is a mock that captures state for testing.

```kotlin
class MockProductPickerView : ProductPickerViewControllable {
    var displayedProducts: List<ProductViewModel> = emptyList()
    var isShowingEmptyState: Boolean = false
    // etc.
}
```

**Swift equivalent**: Your `ClientPickerViewController` with UIKit was abstracted to a testable mock.

### 4. Navigation
**Assumption**: Child RIB presentation is simplified to just attach/detach.

```kotlin
override fun routeToCreateProduct() {
    val router = createProductBuilder.build(interactor)
    attachChild(router)
}
```

**Swift equivalent**: Your modal presentation with `BaseUINavigationController` was simplified since we're in a test environment without UIKit.

### 5. Session/User Context
**Assumption**: User context is passed as a simple `userId` string.

```kotlin
fun build(listener: ProductPickerListener, userId: String)
```

**Swift equivalent**: Your `Session` object was simplified to just a userId parameter.

### 6. Shared Module
**Assumption**: No Kotlin Multiplatform shared module dependency.

Your Swift code imports `shared` (likely a KMP module), but for this test, all types are defined locally to keep it self-contained.

### 7. BaseInteractor
**Assumption**: Used the standard `Interactor` base class.

```kotlin
class ProductPickerInteractor : Interactor()
```

**Swift equivalent**: Your `BaseInteractor<ClientPickerPresentable>` was assumed to have the same behavior as the standard `Interactor`.

## Key Patterns Demonstrated

### 1. Listener Pattern
Parent-child communication through listeners:
```kotlin
interface ProductPickerListener {
    fun didCompleteProductPicker(interactor: ProductPickerInteractable)
    fun didCompleteProductPickerWithProduct(product: Product, ...)
}
```

### 2. Dependency Injection
Hierarchical DI through components:
```kotlin
class ProductPickerComponent(dependency: ProductPickerDependency, userId: String)
    : Component<ProductPickerDependency>(dependency), CreateProductDependency
```

### 3. Child Builder Provision
Component provides child builders:
```kotlin
val createProductBuilder: CreateProductBuildable
    get() = CreateProductBuilder(this)
```

### 4. Reactive Streams
RxJava for async operations with automatic cleanup:
```kotlin
productService.fetchProducts(userId, query)
    .subscribeOn(Schedulers.io())
    .subscribe { products -> /* ... */ }
    .disposeOnDeactivate(this)
```

### 5. View Model Transformation
Presenter converts domain models to view models:
```kotlin
val viewModels = products.map { product ->
    ProductViewModel(
        id = product.id,
        name = product.name,
        formattedPrice = "$${product.priceInCents / 100}"
    )
}
```

## Running the Tests

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests ProductPickerIntegrationTest

# Run with verbose output
./gradlew test --info
```

## Extending This Test

To adapt this pattern for your own RIBs:

1. **Replace Domain Models**: Change `Product` to your domain type
2. **Update Service Interface**: Match your actual service API
3. **Customize View Logic**: Adapt view state to your UI needs
4. **Add Real Dependencies**: Replace mocks with real implementations
5. **Platform-Specific Views**: Implement actual UIViewController/Fragment/etc.

## Notes on Platform Differences

### iOS (Swift) → Kotlin Multiplatform

| Swift | Kotlin |
|-------|--------|
| `UIViewController` | `ViewControllable` (abstraction) |
| `weak var` | Listener interfaces don't need weak refs in tests |
| `async/await` | RxJava `Single`/`Observable` |
| `DispatchQueue` | RxJava `Schedulers` |
| `.onNext()` in ViewController | Separate Presenter class |
| Swift protocols | Kotlin interfaces |
| Associated types | Kotlin generics |

### Testing Strategy

The test uses **synchronous RxJava schedulers** (`Schedulers.trampoline()`) to avoid threading complexity in tests. In production code, you'd use:
- `Schedulers.io()` for background work
- `AndroidSchedulers.mainThread()` or equivalent for UI updates

## Real-World Usage Example

Here's how you'd use this RIB in a real application:

```kotlin
// In your root dependency
class AppComponent : ProductPickerDependency {
    override val productService = RealProductService(httpClient)
    override val analytics = MixpanelAnalytics(apiKey)
}

// In your parent interactor
fun showProductPicker() {
    val builder = ProductPickerBuilder(appComponent)
    val router = builder.build(
        listener = this, // Implement ProductPickerListener
        userId = currentUser.id
    )

    parentRouter.attachChild(router)
    router.load()
}

// Handle callbacks
override fun didCompleteProductPickerWithProduct(
    product: Product,
    interactor: ProductPickerInteractable
) {
    // Use selected product
    processOrder(product)

    // Clean up
    parentRouter.detachChild(productPickerRouter)
}
```

## Summary

This integration test demonstrates that the Kotlin Multiplatform RIBs implementation successfully supports the same architectural patterns as the original Swift/iOS implementation, while remaining platform-agnostic and fully testable.

The test serves as both:
1. **Validation** that the RIBs framework works correctly
2. **Documentation** showing how to structure production RIBs in Kotlin
