package com.uber.rib.integration

import com.uber.rib.*
import com.uber.rib.di.Component
import com.uber.rib.di.Dependency
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive integration test showcasing a real production RIB implementation.
 *
 * This test demonstrates:
 * - Complete RIB hierarchy (Router, Interactor, Builder, Component)
 * - Child RIB routing and lifecycle management
 * - Business logic with service layer integration
 * - Listener pattern for parent-child communication
 * - Dependency injection through components
 * - View abstraction for platform independence
 *
 * Architecture:
 * ProductPickerRIB
 *   ├── ProductPickerRouter (manages navigation)
 *   ├── ProductPickerInteractor (business logic)
 *   ├── ProductPickerPresenter (view model translation)
 *   ├── MockProductPickerView (platform-agnostic view)
 *   └── Child: CreateProductRIB (attached on demand)
 */
class ProductPickerIntegrationTest {

    private lateinit var productService: MockProductService
    private lateinit var analytics: MockAnalytics
    private lateinit var rootDependency: MockRootDependency
    private lateinit var builder: ProductPickerBuilder

    @Before
    fun setup() {
        productService = MockProductService()
        analytics = MockAnalytics()
        rootDependency = MockRootDependency(productService, analytics)
        builder = ProductPickerBuilder(rootDependency)
    }

    @Test
    fun `test full RIB lifecycle - activation, data loading, and deactivation`() {
        // Given: Build the RIB
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val view = router.viewController as MockProductPickerView

        // When: Load and activate the RIB
        router.load()
        interactor.activate()

        // Then: RIB is active and loads data
        assertTrue(interactor.isActive)
        assertTrue(view.isLoading)

        // When: Service returns products
        productService.completeWithProducts(listOf(
            Product("1", "Product A", 1999),
            Product("2", "Product B", 2999)
        ))

        // Then: View displays products
        Thread.sleep(100) // Allow async processing
        assertFalse(view.isLoading)
        assertEquals(2, view.displayedProducts.size)
        assertEquals("Product A", view.displayedProducts[0].name)

        // When: Deactivate
        interactor.deactivate()

        // Then: RIB is inactive
        assertFalse(interactor.isActive)
    }

    @Test
    fun `test search functionality with query filtering`() {
        // Given: Active RIB with products
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val presenter = interactor.presenter as ProductPickerPresenter

        router.load()
        interactor.activate()

        productService.completeWithProducts(listOf(
            Product("1", "Apple iPhone", 99900),
            Product("2", "Samsung Galaxy", 89900),
            Product("3", "Apple iPad", 79900)
        ))
        Thread.sleep(100)

        // When: User searches for "Apple"
        presenter.searchForProducts("Apple")
        Thread.sleep(100)

        // Then: Only Apple products are shown
        val view = router.viewController as MockProductPickerView
        assertEquals(2, view.displayedProducts.size)
        assertTrue(view.displayedProducts.all { it.name.contains("Apple") })

        // Verify analytics tracked the search
        assertTrue(analytics.events.any { it == "Search Products" })
    }

    @Test
    fun `test child RIB routing - attach and detach CreateProduct`() {
        // Given: Active ProductPicker RIB
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor

        router.load()
        interactor.activate()
        productService.completeWithProducts(emptyList())
        Thread.sleep(100)

        // When: User wants to create a new product
        val presenter = interactor.presenter as ProductPickerPresenter
        presenter.addNewProduct()

        // Then: Child RIB is attached
        assertEquals(1, router.children.size)
        val childRouter = router.children.first()
        assertTrue(childRouter.interactable.isActive)

        // When: User completes product creation
        val createProductInteractor = childRouter.interactable as CreateProductInteractor
        createProductInteractor.completeWithProduct(Product("new", "New Product", 5999))
        Thread.sleep(100)

        // Then: Child is detached and listener is notified
        assertEquals(0, router.children.size)
        assertNotNull(listener.selectedProduct)
        assertEquals("New Product", listener.selectedProduct?.name)
    }

    @Test
    fun `test product selection flow with listener callback`() {
        // Given: Active RIB with products
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val presenter = interactor.presenter as ProductPickerPresenter

        router.load()
        interactor.activate()

        productService.completeWithProducts(listOf(
            Product("1", "Product A", 1999),
            Product("2", "Product B", 2999)
        ))
        Thread.sleep(100)

        // When: User selects a product
        presenter.selectProductWithId("1")

        // Then: Listener is called with selected product
        assertNotNull(listener.selectedProduct)
        assertEquals("Product A", listener.selectedProduct?.name)
        assertEquals("1", listener.selectedProduct?.id)

        // Analytics tracked the selection
        assertTrue(analytics.events.any { it == "Select Product" })
    }

    @Test
    fun `test empty state presentation`() {
        // Given: Active RIB
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val view = router.viewController as MockProductPickerView

        router.load()
        interactor.activate()

        // When: Service returns empty list
        productService.completeWithProducts(emptyList())
        Thread.sleep(100)

        // Then: Empty state is shown
        assertTrue(view.isShowingEmptyState)
        assertEquals("No products found. Create your first product!", view.emptyStateMessage)
    }

    @Test
    fun `test error handling and presentation`() {
        // Given: Active RIB
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val view = router.viewController as MockProductPickerView

        router.load()
        interactor.activate()

        // When: Service returns error
        productService.completeWithError(Exception("Network error"))
        Thread.sleep(100)

        // Then: Error is presented
        assertFalse(view.isLoading)
        assertNotNull(view.displayedError)
        assertEquals("Network error", view.displayedError?.message)
    }

    @Test
    fun `test close action with cleanup`() {
        // Given: Active RIB with child attached
        val listener = MockProductPickerListener()
        val router = builder.build(listener, userId = "user123")
        val interactor = router.interactor as ProductPickerInteractor
        val presenter = interactor.presenter as ProductPickerPresenter

        router.load()
        interactor.activate()
        presenter.addNewProduct() // Attach child

        assertEquals(1, router.children.size)

        // When: User closes the picker
        presenter.close()

        // Then: Listener is notified of completion
        assertTrue(listener.didComplete)

        // Analytics tracked the close
        assertTrue(analytics.events.any { it == "Close Product Picker" })
    }
}

// MARK: - Domain Models

data class Product(
    val id: String,
    val name: String,
    val priceInCents: Int
)

data class ProductViewModel(
    val id: String,
    val name: String,
    val formattedPrice: String
)

// MARK: - Service Layer (Mock)

interface ProductService {
    fun fetchProducts(userId: String, query: String?): Single<List<Product>>
}

class MockProductService : ProductService {
    private val subject = PublishSubject.create<List<Product>>()
    private val errorSubject = PublishSubject.create<Throwable>()

    override fun fetchProducts(userId: String, query: String?): Single<List<Product>> {
        return Single.create { emitter ->
            val disposable1 = subject.subscribe { products ->
                val filtered = if (query.isNullOrBlank()) {
                    products
                } else {
                    products.filter { it.name.contains(query, ignoreCase = true) }
                }
                emitter.onSuccess(filtered)
            }

            val disposable2 = errorSubject.subscribe { error ->
                emitter.onError(error)
            }

            emitter.setCancellable {
                disposable1.dispose()
                disposable2.dispose()
            }
        }
    }

    fun completeWithProducts(products: List<Product>) {
        subject.onNext(products)
    }

    fun completeWithError(error: Throwable) {
        errorSubject.onNext(error)
    }
}

// MARK: - Analytics (Mock)

interface Analytics {
    fun track(event: String, properties: Map<String, Any> = emptyMap())
}

class MockAnalytics : Analytics {
    val events = mutableListOf<String>()
    val properties = mutableMapOf<String, Map<String, Any>>()

    override fun track(event: String, properties: Map<String, Any>) {
        events.add(event)
        this.properties[event] = properties
    }
}

// MARK: - ProductPicker Protocols

interface ProductPickerInteractable : Interactable, CreateProductListener {
    var router: ProductPickerRouting?
    var listener: ProductPickerListener?
}

interface ProductPickerViewControllable : ViewControllable {
    // Router can manipulate view hierarchy through this interface
}

interface ProductPickerRouting : ViewableRouting {
    fun routeToCreateProduct()
    fun routeAwayFromCreateProduct(callback: () -> Unit)
}

interface ProductPickerPresentable : Presentable {
    var listener: ProductPickerPresentableListener?

    fun presentProducts(products: List<ProductViewModel>)
    fun presentEmptyState()
    fun presentEmptySearchState()
    fun presentLoading()
    fun hideLoading()
    fun presentError(error: Throwable)
}

interface ProductPickerPresentableListener {
    fun close()
    fun addNewProduct()
    fun selectProductWithId(id: String)
    fun searchForProducts(searchText: String)
}

interface ProductPickerListener {
    fun didCompleteProductPicker(interactor: ProductPickerInteractable)
    fun didCompleteProductPickerWithProduct(product: Product, interactor: ProductPickerInteractable)
}

// MARK: - ProductPicker Router

class ProductPickerRouter(
    interactor: ProductPickerInteractable,
    viewController: ProductPickerViewControllable,
    private val createProductBuilder: CreateProductBuildable
) : ViewableRouter<ProductPickerInteractable, ProductPickerViewControllable>(interactor, viewController),
    ProductPickerRouting {

    private var createProductRouter: CreateProductRouting? = null

    init {
        interactor.router = this
    }

    override fun routeToCreateProduct() {
        val router = createProductBuilder.build(interactor)
        createProductRouter = router

        // In a real app, this would present a modal or navigate
        // For testing, we just attach the child
        attachChild(router)
    }

    override fun routeAwayFromCreateProduct(callback: () -> Unit) {
        createProductRouter?.let { router ->
            detachChild(router)
            createProductRouter = null
            callback()
        }
    }
}

// MARK: - ProductPicker Interactor

class ProductPickerInteractor(
    val presenter: ProductPickerPresentable,
    private val productService: ProductService,
    private val userId: String,
    private val analytics: Analytics
) : Interactor(), ProductPickerInteractable, ProductPickerPresentableListener {

    override var router: ProductPickerRouting? = null
    override var listener: ProductPickerListener? = null

    private var products: List<Product> = emptyList()

    init {
        presenter.listener = this
    }

    override fun didBecomeActive() {
        super.didBecomeActive()
        fetchProducts()
    }

    private fun fetchProducts(query: String? = null) {
        presenter.presentLoading()

        productService.fetchProducts(userId, query)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.trampoline()) // Use trampoline for testing
            .subscribe({ products ->
                this.products = products
                presenter.hideLoading()

                if (products.isEmpty()) {
                    if (query != null) {
                        presenter.presentEmptySearchState()
                    } else {
                        presenter.presentEmptyState()
                    }
                } else {
                    val viewModels = products.map { product ->
                        ProductViewModel(
                            id = product.id,
                            name = product.name,
                            formattedPrice = "$${product.priceInCents / 100}.${product.priceInCents % 100}"
                        )
                    }
                    presenter.presentProducts(viewModels)
                }
            }, { error ->
                presenter.hideLoading()
                presenter.presentError(error)
            })
            .disposeOnDeactivate(this)
    }

    override fun close() {
        analytics.track("Close Product Picker", mapOf("screen" to "Product Picker"))
        listener?.didCompleteProductPicker(this)
    }

    override fun addNewProduct() {
        analytics.track("Add New Product", mapOf("screen" to "Product Picker"))
        router?.routeToCreateProduct()
    }

    override fun selectProductWithId(id: String) {
        analytics.track("Select Product", mapOf("screen" to "Product Picker", "productId" to id))
        val product = products.firstOrNull { it.id == id }
        product?.let {
            listener?.didCompleteProductPickerWithProduct(it, this)
        }
    }

    override fun searchForProducts(searchText: String) {
        analytics.track("Search Products", mapOf("screen" to "Product Picker", "query" to searchText))
        fetchProducts(query = searchText)
    }

    // MARK: - CreateProductListener

    override fun didCompleteCreateProduct(interactor: CreateProductInteractable) {
        router?.routeAwayFromCreateProduct {}
    }

    override fun didCompleteCreateProduct(interactor: CreateProductInteractable, product: Product) {
        router?.routeAwayFromCreateProduct {
            listener?.didCompleteProductPickerWithProduct(product, this)
        }
    }
}

// MARK: - ProductPicker Presenter

class ProductPickerPresenter(
    private val view: MockProductPickerView
) : ProductPickerPresentable {

    override var listener: ProductPickerPresentableListener? = null

    override fun presentProducts(products: List<ProductViewModel>) {
        view.displayProducts(products)
    }

    override fun presentEmptyState() {
        view.showEmptyState("No products found. Create your first product!")
    }

    override fun presentEmptySearchState() {
        view.showEmptyState("No products match your search.")
    }

    override fun presentLoading() {
        view.showLoading()
    }

    override fun hideLoading() {
        view.hideLoading()
    }

    override fun presentError(error: Throwable) {
        view.showError(error)
    }

    // Delegate user actions to listener
    fun close() = listener?.close()
    fun addNewProduct() = listener?.addNewProduct()
    fun selectProductWithId(id: String) = listener?.selectProductWithId(id)
    fun searchForProducts(query: String) = listener?.searchForProducts(query)
}

// MARK: - ProductPicker View (Mock)

/**
 * Mock view implementation for testing.
 * In a real app, this would be:
 * - UIViewController on iOS
 * - Fragment or Activity on Android
 * - Composable or DOM element on Web
 */
class MockProductPickerView : ProductPickerViewControllable {
    override val viewComponent: Any = "MockView"

    var isLoading = false
    var displayedProducts = emptyList<ProductViewModel>()
    var isShowingEmptyState = false
    var emptyStateMessage: String? = null
    var displayedError: Throwable? = null

    fun showLoading() {
        isLoading = true
    }

    fun hideLoading() {
        isLoading = false
    }

    fun displayProducts(products: List<ProductViewModel>) {
        isShowingEmptyState = false
        displayedProducts = products
    }

    fun showEmptyState(message: String) {
        isShowingEmptyState = true
        emptyStateMessage = message
        displayedProducts = emptyList()
    }

    fun showError(error: Throwable) {
        displayedError = error
    }
}

// MARK: - ProductPicker Dependency & Component

interface ProductPickerDependency : Dependency {
    val productService: ProductService
    val analytics: Analytics
}

class ProductPickerComponent(
    dependency: ProductPickerDependency,
    private val userId: String
) : Component<ProductPickerDependency>(dependency), CreateProductDependency {

    val productService: ProductService
        get() = dependency.productService

    val analytics: Analytics
        get() = dependency.analytics

    val createProductBuilder: CreateProductBuildable
        get() = CreateProductBuilder(this)
}

// MARK: - ProductPicker Builder

interface ProductPickerBuildable : Buildable {
    fun build(listener: ProductPickerListener, userId: String): ProductPickerRouter
}

class ProductPickerBuilder(
    dependency: ProductPickerDependency
) : Builder<ProductPickerDependency>(dependency), ProductPickerBuildable {

    override fun build(listener: ProductPickerListener, userId: String): ProductPickerRouter {
        val component = ProductPickerComponent(dependency, userId)
        val view = MockProductPickerView()
        val presenter = ProductPickerPresenter(view)
        val interactor = ProductPickerInteractor(
            presenter = presenter,
            productService = component.productService,
            userId = userId,
            analytics = component.analytics
        )
        interactor.listener = listener

        val router = ProductPickerRouter(
            interactor = interactor,
            viewController = view,
            createProductBuilder = component.createProductBuilder
        )

        return router
    }
}

// MARK: - Mock Root Dependency

class MockRootDependency(
    override val productService: ProductService,
    override val analytics: Analytics
) : ProductPickerDependency

class MockProductPickerListener : ProductPickerListener {
    var didComplete = false
    var selectedProduct: Product? = null

    override fun didCompleteProductPicker(interactor: ProductPickerInteractable) {
        didComplete = true
    }

    override fun didCompleteProductPickerWithProduct(product: Product, interactor: ProductPickerInteractable) {
        didComplete = true
        selectedProduct = product
    }
}

// MARK: - Child RIB: CreateProduct (Simplified)

interface CreateProductInteractable : Interactable {
    var router: CreateProductRouting?
    var listener: CreateProductListener?

    fun completeWithProduct(product: Product)
}

interface CreateProductRouting : ViewableRouting

interface CreateProductListener {
    fun didCompleteCreateProduct(interactor: CreateProductInteractable)
    fun didCompleteCreateProduct(interactor: CreateProductInteractable, product: Product)
}

interface CreateProductDependency : Dependency {
    val analytics: Analytics
}

class CreateProductInteractor(
    private val analytics: Analytics
) : Interactor(), CreateProductInteractable {
    override var router: CreateProductRouting? = null
    override var listener: CreateProductListener? = null

    fun completeWithProduct(product: Product) {
        analytics.track("Create Product Complete", mapOf("productId" to product.id))
        listener?.didCompleteCreateProduct(this, product)
    }
}

class CreateProductRouter(
    interactor: CreateProductInteractable,
    viewController: ViewControllable
) : ViewableRouter<CreateProductInteractable, ViewControllable>(interactor, viewController),
    CreateProductRouting {

    init {
        interactor.router = this
    }
}

interface CreateProductBuildable : Buildable {
    fun build(listener: CreateProductListener): CreateProductRouting
}

class CreateProductBuilder(
    dependency: CreateProductDependency
) : Builder<CreateProductDependency>(dependency), CreateProductBuildable {

    override fun build(listener: CreateProductListener): CreateProductRouting {
        val view = object : ViewControllable {
            override val viewComponent: Any = "CreateProductView"
        }
        val interactor = CreateProductInteractor(dependency.analytics)
        interactor.listener = listener

        return CreateProductRouter(interactor, view)
    }
}
