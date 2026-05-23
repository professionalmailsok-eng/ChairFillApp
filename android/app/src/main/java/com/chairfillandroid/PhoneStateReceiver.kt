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
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null
        private val client = OkHttpClient()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.extras?.getString(TelephonyManager.EXTRA_STATE)
            val number = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

            var state = 0
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
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (isIncoming) {
                    isIncoming = false
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Missed call detected!
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
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }
}