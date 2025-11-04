package com.example.llmapp

import android.content.Context
import android.provider.Settings
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object DeviceIdManager {
    private const val PREFS_NAME = "device_id_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val TAG = "DeviceIdManager"

    private val client = OkHttpClient()

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "Generated and stored new device ID: $deviceId")

            registerDeviceWithServer(context, deviceId)
        } else {
            Log.d(TAG, "Loaded existing device ID: $deviceId")
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            Log.d(TAG, "Existing tokens -> Access: $accessToken | Refresh: $refreshToken")
        }

        return deviceId ?: "unknown"
    }

    private fun registerDeviceWithServer(context: Context, deviceId: String) {
        Thread {
            try {
                val url = "http://192.168.29.230:8000/api/auth/device"
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                }
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        handleTokenResponse(context, response.body?.string())
                    } else if (response.code == 500) {
                        val errorBody = response.body?.string()
                        if (!errorBody.isNullOrEmpty()) {
                            val jsonError = JSONObject(errorBody)
                            val detailMessage = jsonError.optString("detail", "")
                            if (detailMessage.contains("Device already registered", ignoreCase = true)) {
                                Log.w(TAG, "Device already registered. Calling /api/auth/reinit ...")
                                callReinitEndpoint(context, deviceId)
                            } else {
                                Log.e(TAG, "Server error 500: $detailMessage")
                            }
                        } else {
                            Log.e(TAG, "Server error 500 with empty body")
                        }
                    } else {
                        Log.e(TAG, "Failed to register device. Response code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering device: ${e.message}", e)
            }
        }.start()
    }

    private fun callReinitEndpoint(context: Context, deviceId: String) {
        try {
            val url = "http://192.168.29.230:8000/api/auth/reinit"
            val json = JSONObject().apply {
                put("device_id", deviceId)
            }
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { reinitResponse ->
                if (reinitResponse.isSuccessful) {
                    handleTokenResponse(context, reinitResponse.body?.string())
                } else {
                    Log.e(TAG, "Failed to reinit device. Response code: ${reinitResponse.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling reinit endpoint: ${e.message}", e)
        }
    }

    private fun handleTokenResponse(context: Context, responseBody: String?) {
        if (!responseBody.isNullOrEmpty()) {
            val jsonResponse = JSONObject(responseBody)
            val accessToken = jsonResponse.optString("access_token", "")
            val refreshToken = jsonResponse.optString("refresh_token", "")

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()

            Log.d(TAG, "Access Token: $accessToken")
            Log.d(TAG, "Refresh Token: $refreshToken")
        }
    }

    fun getAccessToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REFRESH_TOKEN, null)
}
