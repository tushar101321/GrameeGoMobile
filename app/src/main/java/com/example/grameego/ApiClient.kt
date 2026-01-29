package com.example.grameego

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {


    private const val BASE_URL = "https://grameego-backend.onrender.com/"


    private lateinit var appContext: Context
    private var api: ApiService? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun service(): ApiService {
        val existing = api
        if (existing != null) return existing

        if (!::appContext.isInitialized) {
            throw IllegalStateException("ApiClient.init(context) not called")
        }

        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val token = SessionManager.getToken(appContext)
            val newReq = if (!token.isNullOrBlank()) {
                req.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else req
            chain.proceed(newReq)
        }

        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ApiService::class.java)
        return api!!
    }
}

object SessionManager {
    private const val PREF = "grameego_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_ROLE = "role"
    private const val KEY_NAME = "name"

    fun saveAuth(context: Context, token: String, user: UserDto) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_NAME, user.name)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun getRole(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ROLE, null)

    fun getName(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_NAME, null)
}
