package com.chairfillandroid

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Bridges the clinic config (clinic_id + monitoring on/off) from the JS UI to native
 * SharedPreferences so the background PhoneStateReceiver can read the REAL clinic id
 * and honour the on/off toggle. Without this the receiver used a hard-coded fake
 * clinic_id and ignored the toggle.
 */
class ConfigModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName() = "ConfigModule"

  @ReactMethod
  fun saveConfig(deviceKey: String, isActive: Boolean, promise: Promise) {
    try {
      reactApplicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DEVICE_KEY, deviceKey.trim())
        .putBoolean(KEY_IS_ACTIVE, isActive)
        .apply()
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("SAVE_FAILED", e)
    }
  }

  @ReactMethod
  fun getConfig(promise: Promise) {
    try {
      val prefs = reactApplicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      val map = Arguments.createMap()
      map.putString(KEY_DEVICE_KEY, prefs.getString(KEY_DEVICE_KEY, ""))
      map.putBoolean(KEY_IS_ACTIVE, prefs.getBoolean(KEY_IS_ACTIVE, false))
      promise.resolve(map)
    } catch (e: Exception) {
      promise.reject("GET_FAILED", e)
    }
  }

  companion object {
    const val PREFS = "ChairFillPrefs"
    const val KEY_DEVICE_KEY = "device_key"
    const val KEY_IS_ACTIVE = "is_active"
  }
}
