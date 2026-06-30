package com.chairfillandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.concurrent.thread

class PhoneStateReceiver : BroadcastReceiver() {
    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null
        private val client = OkHttpClient()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.extras?.getString(TelephonyManager.EXTRA_STATE)
            val number = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            if (!number.isNullOrEmpty()) {
                savedNumber = number
            }

            var state = TelephonyManager.CALL_STATE_IDLE
            if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                state = TelephonyManager.CALL_STATE_IDLE
            } else if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                state = TelephonyManager.CALL_STATE_OFFHOOK
            } else if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                state = TelephonyManager.CALL_STATE_RINGING
            }

            onCallStateChanged(context, state, number)
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) {
            return
        }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                if (!number.isNullOrEmpty()) savedNumber = number
                Log.d("PhoneStateReceiver", "Incoming call ringing from: $savedNumber")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (isIncoming) {
                    isIncoming = false
                    Log.d("PhoneStateReceiver", "Incoming call answered")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    Log.d("PhoneStateReceiver", "Missed call detected. Number from intent is: $savedNumber")

                    val numberAtRing = savedNumber
                    val appContext = context.applicationContext

                    // Resolve the number (with Call Log fallback) and send OFF the main thread.
                    // onReceive runs on the main thread, so Thread.sleep + network here would risk an ANR.
                    thread {
                        var finalNumber = numberAtRing
                        if (finalNumber.isNullOrEmpty()) {
                            Log.d("PhoneStateReceiver", "Number in intent is null, checking Call Log...")
                            try { Thread.sleep(800) } catch (e: Exception) {}
                            finalNumber = getLastMissedCallNumber(appContext)
                        }

                        if (!finalNumber.isNullOrEmpty()) {
                            Log.d("PhoneStateReceiver", "Final Missed call from: $finalNumber")
                            sendMissedCallToServer(appContext, finalNumber)
                        } else {
                            Log.e("PhoneStateReceiver", "Could not get the phone number even from Call Log.")
                        }
                    }
                }
                isIncoming = false
                savedNumber = null
            }
        }
        lastState = state
    }

    private fun getLastMissedCallNumber(context: Context): String? {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    if (numIndex != -1) {
                        val num = it.getString(numIndex)
                        Log.d("PhoneStateReceiver", "Found missed call number in Call Log: $num")
                        return num
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("PhoneStateReceiver", "SecurityException reading Call Log. Is READ_CALL_LOG granted?", e)
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Error reading Call Log", e)
        }
        return null
    }

    private fun sendMissedCallToServer(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return

        // Read the device key + on/off toggle that the app saved (ConfigModule -> SharedPreferences).
        val prefs = context.getSharedPreferences("ChairFillPrefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_active", false)
        val deviceKey = prefs.getString("device_key", "")?.trim() ?: ""

        if (!isActive) {
            Log.d("PhoneStateReceiver", "Monitoring is OFF — not sending missed call.")
            return
        }
        if (deviceKey.isEmpty()) {
            Log.e("PhoneStateReceiver", "No device key configured. Open the ChairFill app and paste your device key.")
            return
        }

        // Keep digits/+ only so the JSON body can't be broken by unexpected characters.
        val safeNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (safeNumber.isEmpty()) return

        Log.d("PhoneStateReceiver", "Sending missed call: $safeNumber")

        // IMPORTANT: the *.workers.dev domain does not route at the edge — only chairfill.in does.
        val url = "https://chairfill.in/api/calls/missed"

        val ts = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        // Auth via the per-clinic device key (header); the server resolves the clinic from it.
        val json = """{"phone_number":"$safeNumber","timestamp":"$ts"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("X-Device-Key", deviceKey)
            .header("Connection", "close")
            .build()

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("PhoneStateReceiver", "API Call Failed. Reason: ${e.localizedMessage}", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d("PhoneStateReceiver", "API Call Success: ${response.code}")
                    response.close() // Memory secure release
                }
            })
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Crash prevented in network call", e)
        }
    }
}