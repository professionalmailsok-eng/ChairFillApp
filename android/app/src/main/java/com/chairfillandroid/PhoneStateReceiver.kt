package com.chairfillandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class PhoneStateReceiver : BroadcastReceiver() {
    companion object {
        // FIX: Changed from String to Int (CALL_STATE_IDLE)
        private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming: Boolean = false
        private var savedNumber: String? = null
        private val client: OkHttpClient = OkHttpClient()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.extras?.getString(TelephonyManager.EXTRA_STATE)
            val number = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

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
                savedNumber = number
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
                    // Ringing stopped, but was never answered -> MISSED CALL
                    Log.d("PhoneStateReceiver", "Missed call from: $savedNumber")
                    sendMissedCallToServer(savedNumber)
                }
                isIncoming = false
            }
        }
        lastState = state
    }

    private fun sendMissedCallToServer(phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return
        Log.d("PhoneStateReceiver", "Sending to server: $phoneNumber")
        
        // POST to your backend Webhook URL
        val url = "https://api.chairfill.com/webhook/missed-calls"
        val json = """{"phoneNumber":"$phoneNumber", "status":"missed"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PhoneStateReceiver", "API Call Failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("PhoneStateReceiver", "API Call Success: ${response.code}")
            }
        })
    }
}