package com.example.grameego

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import retrofit2.HttpException

class DriverActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var btnToggle: Button
    private lateinit var btnRefresh: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvHeader: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rv: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout

    private var showAssigned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_driver)

        btnLogout = findViewById(R.id.btnLogout)
        btnToggle = findViewById(R.id.btnToggle)
        btnRefresh = findViewById(R.id.btnRefresh)
        progress = findViewById(R.id.progress)
        tvHeader = findViewById(R.id.tvHeader)
        tvEmpty = findViewById(R.id.tvEmptyDriver)
        rv = findViewById(R.id.rv)
        swipe = findViewById(R.id.swipeDriver)

        tvHeader.text = "Driver - ${SessionManager.getName(this) ?: ""}".trim()
        rv.layoutManager = LinearLayoutManager(this)

        btnLogout.setOnClickListener {
            SessionManager.clear(this)
            finish()
        }

        btnToggle.setOnClickListener {
            showAssigned = !showAssigned
            updateMode()
            load()
        }

        btnRefresh.setOnClickListener {

            stopSwipe()
            load()
        }

        swipe.setOnRefreshListener {
            load(fromSwipe = true)
        }

        updateMode()
        load()
    }

    private fun updateMode() {
        btnToggle.text = if (showAssigned) "Show Available" else "Show Assigned"
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefresh.isEnabled = !loading
        btnToggle.isEnabled = !loading
        btnLogout.isEnabled = !loading
    }

    private fun stopSwipe() {
        swipe.isRefreshing = false
    }

    private fun handle401IfNeeded(e: Exception): Boolean {
        val http = e as? HttpException ?: return false
        if (http.code() == 401) {

            stopSwipe()
            SessionManager.clear(this)
            toast("Session expired. Please login again.")
            finish()
            return true
        }
        return false
    }

    private fun load(fromSwipe: Boolean = false) {
        if (!fromSwipe) {
            setLoading(true)
            stopSwipe()
        }

        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val list = if (showAssigned) api.assignedToMe() else api.availableDeliveries()

                rv.adapter = DriverAdapter(
                    items = list,
                    showAssigned = showAssigned,
                    onAccept = { id -> accept(id) },
                    onPicked = { id -> status(id, "Picked") },
                    onDelivered = { id -> status(id, "Delivered") },
                    onUnassign = { id -> unassign(id) },
                    onNavigate = { dest -> openGoogleNavigation(dest) },
                    onDial = { number -> openDialer(number) }
                )

                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {

                if (!handle401IfNeeded(e)) {
                    toast("Load failed: ${e.message}")
                }
            } finally {
                setLoading(false)
                stopSwipe()
            }
        }
    }

    private fun accept(id: String) = callAndReload {
        ApiClient.service().acceptDelivery(id)
    }

    private fun status(id: String, newStatus: String) = callAndReload {
        ApiClient.service().updateStatus(id, StatusUpdateRequest(newStatus))
    }

    private fun unassign(id: String) = callAndReload {
        ApiClient.service().unassign(id)
    }

    private fun callAndReload(block: suspend () -> Any) {
        setLoading(true)
        stopSwipe()

        lifecycleScope.launch {
            try {
                block()
                load()
            } catch (e: Exception) {
                if (!handle401IfNeeded(e)) {
                    toast("Action failed: ${e.message}")
                }
            } finally {
                setLoading(false)
                stopSwipe()
            }
        }
    }

    private fun openDialer(numberRaw: String) {
        val n = numberRaw.trim()
        if (n.isBlank()) {
            toast("No contact number.")
            return
        }
        val cleaned = n.replace(" ", "")
        try {
            startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$cleaned") })
        } catch (e: Exception) {
            toast("No dialer app found.")
        }
    }

    private fun openGoogleNavigation(destination: String) {
        if (destination.isBlank()) {
            toast("No destination.")
            return
        }
        val encoded = Uri.encode(destination)
        val gmm = Uri.parse("google.navigation:q=$encoded&mode=l")
        val intent = Intent(Intent.ACTION_VIEW, gmm).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded")
                )
            )
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    class DriverAdapter(
        private val items: List<DeliveryDto>,
        private val showAssigned: Boolean,
        private val onAccept: (String) -> Unit,
        private val onPicked: (String) -> Unit,
        private val onDelivered: (String) -> Unit,
        private val onUnassign: (String) -> Unit,
        private val onNavigate: (String) -> Unit,
        private val onDial: (String) -> Unit
    ) : RecyclerView.Adapter<DriverAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val btnA: Button = v.findViewById(R.id.btnA)
            val btnB: Button = v.findViewById(R.id.btnB)
            val btnC: Button = v.findViewById(R.id.btnC)
            val btnD: Button = v.findViewById(R.id.btnD)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_driver_actions, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = items[position]
            val shop = d.shopName ?: "Shop"
            val status = d.deliveryStatus ?: "?"
            val village = d.village ?: ""
            val shopAddr = d.shopAddress ?: ""
            val contact = d.contactNumber ?: ""

            holder.title.text = "$shop • $status"
            holder.sub.text =
                "Village: $village • Contact: $contact • £${"%.2f".format(d.price ?: 0.0)}"
            holder.sub.setOnClickListener { if (contact.isNotBlank()) onDial(contact) }

            val destination = listOfNotNull(
                village.takeIf { it.isNotBlank() },
                shopAddr.takeIf { it.isNotBlank() },
                shop.takeIf { it.isNotBlank() }
            ).joinToString(", ")

            if (!showAssigned) {
                holder.btnA.visibility = View.VISIBLE
                holder.btnB.visibility = View.VISIBLE
                holder.btnC.visibility = View.VISIBLE
                holder.btnD.visibility = View.GONE

                holder.btnA.text = "Accept"
                holder.btnB.text = "Navigate"
                holder.btnC.text = "Call"

                holder.btnA.setOnClickListener { onAccept(d.id) }
                holder.btnB.setOnClickListener { onNavigate(destination) }
                holder.btnC.setOnClickListener { onDial(contact) }
            } else {
                holder.btnA.visibility = View.VISIBLE
                holder.btnB.visibility = View.VISIBLE
                holder.btnC.visibility = View.VISIBLE
                holder.btnD.visibility = View.VISIBLE

                holder.btnA.text = "Picked"
                holder.btnB.text = "Delivered"
                holder.btnC.text = "Navigate"
                holder.btnD.text = "Call"

                holder.btnA.setOnClickListener { onPicked(d.id) }
                holder.btnB.setOnClickListener { onDelivered(d.id) }
                holder.btnC.setOnClickListener { onNavigate(destination) }
                holder.btnD.setOnClickListener { onDial(contact) }

                if (status == "Delivered") {
                    holder.btnA.visibility = View.GONE
                    holder.btnB.visibility = View.GONE
                    holder.btnC.visibility = View.VISIBLE
                    holder.btnD.visibility = View.VISIBLE
                    holder.btnC.text = "Navigate"
                    holder.btnD.text = "Call"
                    holder.btnC.setOnClickListener { onNavigate(destination) }
                    holder.btnD.setOnClickListener { onDial(contact) }
                }
            }
        }
    }
}
