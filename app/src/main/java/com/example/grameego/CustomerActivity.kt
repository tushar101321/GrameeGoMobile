
package com.example.grameego

import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import kotlin.math.roundToInt

class CustomerActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var btnRefreshShops: Button
    private lateinit var btnRefreshOrders: Button
    private lateinit var btnUseMyLocation: Button

    private lateinit var tvHeader: TextView
    private lateinit var tvSelectedShop: TextView
    private lateinit var tvCartShop: TextView
    private lateinit var tvCheckoutShop: TextView
    private lateinit var tvCartCheckout: TextView
    private lateinit var tvEmptyOrders: TextView
    private lateinit var progress: ProgressBar

    private lateinit var shopsRecycler: RecyclerView
    private lateinit var productsRecycler: RecyclerView
    private lateinit var ordersRecycler: RecyclerView

    private lateinit var swipeOrders: SwipeRefreshLayout

    private lateinit var etContact: EditText
    private lateinit var etVillage: EditText
    private lateinit var etDistance: EditText
    private lateinit var etNeedBy: EditText
    private lateinit var btnPlaceOrder: Button

    private lateinit var blockShop: View
    private lateinit var blockCheckout: View
    private lateinit var blockOrders: View
    private lateinit var bottomNav: BottomNavigationView

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val cart = linkedMapOf<String, CartLine>()
    private var selectedShop: ShopDetail? = null

    data class CartLine(val product: ProductDto, var qty: Int)

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine || coarse) fetchAndFillLocation() else toast("Location permission denied.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_customer)

        btnLogout = findViewById(R.id.btnLogout)
        btnRefreshShops = findViewById(R.id.btnRefreshShops)
        btnRefreshOrders = findViewById(R.id.btnRefreshOrders)
        btnUseMyLocation = findViewById(R.id.btnUseMyLocation)

        tvHeader = findViewById(R.id.tvHeader)
        tvSelectedShop = findViewById(R.id.tvSelectedShop)
        tvCartShop = findViewById(R.id.tvCartShop)
        tvCheckoutShop = findViewById(R.id.tvCheckoutShop)
        tvCartCheckout = findViewById(R.id.tvCartCheckout)
        tvEmptyOrders = findViewById(R.id.tvEmptyOrders)
        progress = findViewById(R.id.progress)

        shopsRecycler = findViewById(R.id.rvShops)
        productsRecycler = findViewById(R.id.rvProducts)
        ordersRecycler = findViewById(R.id.rvOrders)

        swipeOrders = findViewById(R.id.swipeOrders)

        etContact = findViewById(R.id.etContact)
        etVillage = findViewById(R.id.etVillage)
        etDistance = findViewById(R.id.etDistance)
        etNeedBy = findViewById(R.id.etNeedBy)
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder)

        blockShop = findViewById(R.id.blockShop)
        blockCheckout = findViewById(R.id.blockCheckout)
        blockOrders = findViewById(R.id.blockOrders)
        bottomNav = findViewById(R.id.bottomNavCustomer)

        tvHeader.text = "Customer - ${SessionManager.getName(this) ?: ""}".trim()

        shopsRecycler.layoutManager = LinearLayoutManager(this)
        productsRecycler.layoutManager = LinearLayoutManager(this)
        ordersRecycler.layoutManager = LinearLayoutManager(this)

        btnLogout.setOnClickListener { SessionManager.clear(this); finish() }
        btnRefreshShops.setOnClickListener { loadShops() }
        btnRefreshOrders.setOnClickListener { loadMyOrders() }
        btnPlaceOrder.setOnClickListener { placeOrder() }
        btnUseMyLocation.setOnClickListener { requestLocationPermissionThenFetch() }

        // Pull-to-refresh orders
        swipeOrders.setOnRefreshListener { loadMyOrders(fromSwipe = true) }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_shop -> { showTab(Tab.SHOP); true }
                R.id.nav_checkout -> { showTab(Tab.CHECKOUT); true }
                R.id.nav_orders -> { showTab(Tab.ORDERS); loadMyOrders(); true }
                else -> false
            }
        }

        bottomNav.selectedItemId = R.id.nav_shop
        updateCartText()
        loadShops()
    }

    private enum class Tab { SHOP, CHECKOUT, ORDERS }

    private fun showTab(tab: Tab) {
        blockShop.visibility = if (tab == Tab.SHOP) View.VISIBLE else View.GONE
        blockCheckout.visibility = if (tab == Tab.CHECKOUT) View.VISIBLE else View.GONE
        blockOrders.visibility = if (tab == Tab.ORDERS) View.VISIBLE else View.GONE

        val shop = selectedShop
        tvCheckoutShop.text = if (shop == null) "Shop: none" else "Shop: ${shop.name} (${shop.address})"
        updateCartText()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefreshShops.isEnabled = !loading
        btnRefreshOrders.isEnabled = !loading
        btnPlaceOrder.isEnabled = !loading
        btnUseMyLocation.isEnabled = !loading
        btnLogout.isEnabled = !loading
    }

    private fun handle401IfNeeded(e: Exception): Boolean {
        val http = e as? HttpException ?: return false
        if (http.code() == 401) {
            SessionManager.clear(this)
            toast("Session expired. Please login again.")
            finish()
            return true
        }
        return false
    }

    // -------- LOCATION ----------
    private fun requestLocationPermissionThenFetch() {
        locationPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun fetchAndFillLocation() {
        setLoading(true)
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    setLoading(false)
                    toast("Could not get location. Set a GPS point in emulator Extended Controls.")
                    return@addOnSuccessListener
                }
                val lat = loc.latitude
                val lng = loc.longitude

                lifecycleScope.launch {
                    val resolved = reverseGeocode(lat, lng)
                    etVillage.setText(resolved ?: "${"%.5f".format(lat)}, ${"%.5f".format(lng)}")
                    toast("Location filled.")
                    setLoading(false)
                }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Location failed: ${it.message}")
            }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@CustomerActivity, Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lng, 1)
                if (!list.isNullOrEmpty()) list[0].getAddressLine(0) else null
            } catch (_: Exception) { null }
        }
    }

    // -------- SHOPS ----------
    private fun loadShops() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val shops = api.listShops()
                shopsRecycler.adapter = ShopsAdapter(shops) { loadShopDetail(it) }
            } catch (e: Exception) {
                if (handle401IfNeeded(e)) return@launch
                toast("Failed to load shops: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadShopDetail(shopId: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val detail = api.getShop(shopId)
                selectedShop = detail
                cart.clear()
                tvSelectedShop.text = "Selected: ${detail.name} (${detail.address})"
                updateCartText()
                productsRecycler.adapter = ProductsAdapter(detail.products, { addToCart(it) }, { removeFromCart(it) })
            } catch (e: Exception) {
                if (handle401IfNeeded(e)) return@launch
                toast("Failed to load shop: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun addToCart(p: ProductDto) {
        val line = cart[p.id]
        if (line == null) cart[p.id] = CartLine(p, 1) else line.qty += 1
        updateCartText()
        productsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun removeFromCart(p: ProductDto) {
        val line = cart[p.id] ?: return
        line.qty -= 1
        if (line.qty <= 0) cart.remove(p.id)
        updateCartText()
        productsRecycler.adapter?.notifyDataSetChanged()
    }

    private fun productTotal(): Double = cart.values.sumOf { it.product.price * it.qty }

    private fun updateCartText() {
        val itemsCount = cart.values.sumOf { it.qty }
        val total = productTotal()
        val text = "Cart: $itemsCount items | Product Total: £${"%.2f".format(total)}"
        tvCartShop.text = text
        tvCartCheckout.text = text
    }

    // -------- ORDER ----------
    private fun placeOrder() {
        val shop = selectedShop
        if (shop == null) { toast("Select a shop first."); bottomNav.selectedItemId = R.id.nav_shop; return }
        if (cart.isEmpty()) { toast("Cart is empty."); bottomNav.selectedItemId = R.id.nav_shop; return }

        val contact = etContact.text.toString().trim()
        val village = etVillage.text.toString().trim()
        if (contact.isBlank() || village.isBlank()) { toast("Contact and village/address are required."); return }

        val dist = etDistance.text.toString().trim().toDoubleOrNull()
        val needBy = etNeedBy.text.toString().trim().ifBlank { null }

        val pTotal = productTotal()
        val deliveryFee = computeFee(dist)
        val grand = pTotal + deliveryFee
        val desc = cart.values.joinToString(", ") { "${it.product.name} x${it.qty}" }

        val confirmMsg = """
            Shop: ${shop.name}
            Items: $desc

            Product Total: £${"%.2f".format(pTotal)}
            Delivery Fee(estimate): £${"%.2f".format(deliveryFee)}
            Grand Total: £${"%.2f".format(grand)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Confirm Order")
            .setMessage(confirmMsg)
            .setPositiveButton("Place") { _, _ ->
                doCreateDelivery(shop, desc, contact, village, dist, needBy, deliveryFee, pTotal, grand)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doCreateDelivery(
        shop: ShopDetail,
        itemDescription: String,
        contact: String,
        village: String,
        dist: Double?,
        needByAt: String?,
        deliveryFee: Double,
        productTotal: Double,
        grandTotal: Double
    ) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val items = cart.values.map { ItemSnapshot(it.product.id, it.product.name, it.qty, it.product.price) }
                val req = CreateDeliveryRequest(itemDescription, contact, village, shop.name, shop.address, dist, needByAt, items, productTotal, deliveryFee, grandTotal, shop.id)
                api.createDelivery(req)
                toast("Order placed.")
                cart.clear()
                updateCartText()
                bottomNav.selectedItemId = R.id.nav_orders
            } catch (e: Exception) {
                if (handle401IfNeeded(e)) return@launch
                toast("Order failed: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    // -------- MY ORDERS ----------
    private fun loadMyOrders(fromSwipe: Boolean = false) {
        if (!fromSwipe) setLoading(true)
        tvEmptyOrders.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val list = api.myDeliveries()

                ordersRecycler.adapter = OrdersAdapter(list, { cancelOrder(it) }, { openDialer(it) })

                tvEmptyOrders.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (handle401IfNeeded(e)) return@launch
                toast("Failed to load orders: ${e.message}")
            } finally {
                if (!fromSwipe) setLoading(false)
                swipeOrders.isRefreshing = false
            }
        }
    }

    private fun cancelOrder(id: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                api.cancelDelivery(id)
                toast("Cancelled.")
                loadMyOrders()
            } catch (e: Exception) {
                if (handle401IfNeeded(e)) return@launch
                toast("Cancel failed: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun openDialer(numberRaw: String) {
        val number = numberRaw.trim()
        if (number.isBlank()) { toast("No driver contact number."); return }
        val cleaned = number.replace(" ", "")
        startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$cleaned") })
    }

    private fun computeFee(distanceKm: Double?): Double {
        if (distanceKm != null && distanceKm > 0) {
            val fee = 2.0 + 0.6 * distanceKm
            val rounded = (fee * 100.0).roundToInt() / 100.0
            return maxOf(3.0, rounded)
        }
        return 4.0
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ----------------- ADAPTERS -----------------
    class ShopsAdapter(private val items: List<ShopSummary>, private val onClick: (String) -> Unit)
        : RecyclerView.Adapter<ShopsAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val btn: Button = v.findViewById(R.id.btnAction)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.row_simple_action, parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.title.text = s.name
            holder.sub.text = "${s.address} • ${s.productsCount} products"
            holder.btn.text = "Open"
            holder.btn.setOnClickListener { onClick(s.id) }
            holder.itemView.setOnClickListener { onClick(s.id) }
            holder.itemView.findViewById<TextView>(R.id.tvContact).visibility = View.GONE
        }
    }

    inner class ProductsAdapter(
        private val items: List<ProductDto>,
        private val onPlus: (ProductDto) -> Unit,
        private val onMinus: (ProductDto) -> Unit
    ) : RecyclerView.Adapter<ProductsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val btnMinus: Button = v.findViewById(R.id.btnMinus)
            val btnPlus: Button = v.findViewById(R.id.btnPlus)
            val tvQty: TextView = v.findViewById(R.id.tvQty)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.row_product_cart, parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.title.text = p.name
            holder.sub.text = "£${"%.2f".format(p.price)}"
            val qty = cart[p.id]?.qty ?: 0
            holder.tvQty.text = qty.toString()
            holder.btnPlus.setOnClickListener { onPlus(p) }
            holder.btnMinus.setOnClickListener { onMinus(p) }
        }
    }

    class OrdersAdapter(
        private val items: List<DeliveryDto>,
        private val onCancel: (String) -> Unit,
        private val onDial: (String) -> Unit
    ) : RecyclerView.Adapter<OrdersAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val contact: TextView = v.findViewById(R.id.tvContact)
            val btn: Button = v.findViewById(R.id.btnAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.row_simple_action, parent, false))

        override fun getItemCount() = items.size

        private fun extractDriverMobile(assignedDriver: JsonElement?): String? {
            if (assignedDriver == null || assignedDriver.isJsonNull) return null
            return try {
                if (assignedDriver.isJsonObject) assignedDriver.asJsonObject.get("mobile")?.asString?.trim()
                else null
            } catch (_: Exception) { null }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = items[position]
            val status = d.deliveryStatus ?: "?"
            val shop = d.shopName ?: "Shop"

            holder.title.text = "$shop • $status"
            holder.sub.text = "ShopConfirm: ${d.shopConfirmationStatus ?: "?"} • £${"%.2f".format(d.price ?: 0.0)}"

            val driverMobile = extractDriverMobile(d.assignedDriver)
            if (!driverMobile.isNullOrBlank()) {
                holder.contact.visibility = View.VISIBLE
                holder.contact.text = "Contact driver: $driverMobile (tap to dial)"
                holder.contact.setOnClickListener { onDial(driverMobile) }
            } else {
                holder.contact.visibility = View.VISIBLE
                holder.contact.text = "Driver not assigned yet"
                holder.contact.setOnClickListener(null)
            }

            if (status == "Pending") {
                holder.btn.visibility = View.VISIBLE
                holder.btn.text = "Cancel"
                holder.btn.setOnClickListener { onCancel(d.id) }
            } else holder.btn.visibility = View.GONE
        }
    }
}
