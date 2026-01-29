package com.example.grameego

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var rgMode: RadioGroup
    private lateinit var rbLogin: RadioButton
    private lateinit var rbSignup: RadioButton

    private lateinit var etName: EditText
    private lateinit var etMobile: EditText
    private lateinit var etPassword: EditText

    private lateinit var spRole: Spinner
    private lateinit var etVillage: EditText
    private lateinit var etVehicle: EditText
    private lateinit var spShopId: Spinner

    private lateinit var btnSubmit: Button
    private lateinit var btnLogout: Button
    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar

    private var shops: List<ShopSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_auth)

        rgMode = findViewById(R.id.rgMode)
        rbLogin = findViewById(R.id.rbLogin)
        rbSignup = findViewById(R.id.rbSignup)

        etName = findViewById(R.id.etName)
        etMobile = findViewById(R.id.etMobile)
        etPassword = findViewById(R.id.etPassword)

        spRole = findViewById(R.id.spRole)
        etVillage = findViewById(R.id.etVillage)
        etVehicle = findViewById(R.id.etVehicle)
        spShopId = findViewById(R.id.spShopId)

        btnSubmit = findViewById(R.id.btnSubmit)
        btnLogout = findViewById(R.id.btnLogout)
        tvStatus = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progress)

        spRole.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("customer", "driver", "shop")
        )

        spShopId.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Loading shops...")
        )

        rgMode.setOnCheckedChangeListener { _, _ -> updateUiForModeAndRole() }
        spRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateUiForModeAndRole()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnLogout.setOnClickListener {
            SessionManager.clear(this)
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        }

        btnSubmit.setOnClickListener {
            submit()
        }

        updateUiForModeAndRole()
        loadShopsForShopSignup()
    }

    private fun updateUiForModeAndRole() {
        val isSignup = rbSignup.isChecked
        val role = spRole.selectedItem?.toString() ?: "customer"

        etName.visibility = if (isSignup) View.VISIBLE else View.GONE
        spRole.visibility = if (isSignup) View.VISIBLE else View.GONE

        etVillage.visibility = if (isSignup && role == "customer") View.VISIBLE else View.GONE
        etVehicle.visibility = if (isSignup && role == "driver") View.VISIBLE else View.GONE
        spShopId.visibility = if (isSignup && role == "shop") View.VISIBLE else View.GONE

        btnSubmit.text = if (isSignup) "Sign Up" else "Login"
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
        btnLogout.isEnabled = !loading
    }

    private fun loadShopsForShopSignup() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                shops = api.listShops()
                val ids = shops.map { "${it.id} - ${it.name}" }
                spShopId.adapter = ArrayAdapter(
                    this@AuthActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    if (ids.isEmpty()) listOf("No shops") else ids
                )
            } catch (e: Exception) {
                spShopId.adapter = ArrayAdapter(
                    this@AuthActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Failed to load shops")
                )
            }
        }
    }

    private fun submit() {
        val isSignup = rbSignup.isChecked

        val name = etName.text.toString().trim()
        val mobile = etMobile.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (mobile.isBlank() || password.isBlank() || (isSignup && name.isBlank())) {
            tvStatus.text = "Fill required fields."
            return
        }

        setLoading(true)
        tvStatus.text = ""

        lifecycleScope.launch {
            try {
                val api = ApiClient.service()
                val resp = if (!isSignup) {
                    api.login(LoginRequest(mobile, password))
                } else {
                    val role = spRole.selectedItem.toString()

                    val village = if (role == "customer") etVillage.text.toString().trim() else null
                    val vehicle = if (role == "driver") etVehicle.text.toString().trim() else null

                    val shopId = if (role == "shop") {
                        val idx = spShopId.selectedItemPosition
                        if (idx < 0 || idx >= shops.size) null else shops[idx].id
                    } else null

                    api.signup(
                        SignupRequest(
                            name = name,
                            mobile = mobile,
                            password = password,
                            role = role,
                            village = village,
                            vehicleType = vehicle,
                            shopId = shopId
                        )
                    )
                }

                SessionManager.saveAuth(this@AuthActivity, resp.token, resp.user)

                when (resp.user.role) {
                    "customer" -> startActivity(Intent(this@AuthActivity, CustomerActivity::class.java))
                    "driver" -> startActivity(Intent(this@AuthActivity, DriverActivity::class.java))
                    "shop" -> startActivity(Intent(this@AuthActivity, ShopActivity::class.java))
                    else -> {
                        tvStatus.text = "Unknown role."
                        SessionManager.clear(this@AuthActivity)
                        return@launch
                    }
                }
                finish()
            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message ?: "request failed"}"
            } finally {
                setLoading(false)
            }
        }
    }
}
