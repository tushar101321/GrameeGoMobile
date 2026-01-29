package com.example.grameego

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ShopActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var btnRefresh: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvHeader: TextView
    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_shop)

        btnLogout = findViewById(R.id.btnLogout)
        btnRefresh = findViewById(R.id.btnRefresh)
        progress = findViewById(R.id.progress)
        tvHeader = findViewById(R.id.tvHeader)
        rv = findViewById(R.id.rv)

        tvHeader.text = "Shop - ${SessionManager.getName(this) ?: ""}".trim()

        rv.layoutManager = LinearLayoutManager(this)

        btnLogout.setOnClickListener {
            SessionManager.clear(this)
            finish()
        }
        btnRefresh.setOnClickListener { loadOrders() }

        loadOrders()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefresh.isEnabled = !loading
    }

    private fun loadOrders() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val list = api.shopMyOrders()
                rv.adapter = ShopOrdersAdapter(
                    list,
                    onAccept = { id -> confirm(id, "accept") },
                    onReject = { id -> confirm(id, "reject") }
                )
            } catch (e: Exception) {
                toast("Load failed: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun confirm(id: String, action: String) {
        val input = EditText(this)
        input.hint = "Optional note (max ~300)"

        AlertDialog.Builder(this)
            .setTitle(if (action == "accept") "Accept order?" else "Reject order?")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                doConfirm(id, action, input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doConfirm(id: String, action: String, note: String?) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                api.shopConfirm(id, ConfirmOrderRequest(action = action, note = note?.ifBlank { null }))
                toast("Updated.")
                loadOrders()
            } catch (e: Exception) {
                toast("Update failed: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    class ShopOrdersAdapter(
        private val items: List<DeliveryDto>,
        private val onAccept: (String) -> Unit,
        private val onReject: (String) -> Unit
    ) : RecyclerView.Adapter<ShopOrdersAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val btnA: Button = v.findViewById(R.id.btnA)
            val btnB: Button = v.findViewById(R.id.btnB)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.row_shop_actions, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = items[position]
            holder.title.text = "${d.itemDescription ?: "Order"} • ${d.deliveryStatus ?: "?"}"
            holder.sub.text = "Village: ${d.village ?: ""} • Contact: ${d.contactNumber ?: ""} • Confirm: ${d.shopConfirmationStatus ?: "?"}"

            val already = d.shopConfirmationStatus
            val canAct = already != "Rejected" && already != "Accepted"

            holder.btnA.text = "Accept"
            holder.btnB.text = "Reject"

            holder.btnA.isEnabled = canAct
            holder.btnB.isEnabled = canAct

            holder.btnA.setOnClickListener { onAccept(d.id) }
            holder.btnB.setOnClickListener { onReject(d.id) }
        }
    }
}
