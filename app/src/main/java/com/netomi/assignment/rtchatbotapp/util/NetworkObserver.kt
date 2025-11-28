package com.netomi.assignment.rtchatbotapp.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkObserver(private val context: Context) {
    @SuppressLint("ServiceCast")
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.tryEmit(true)
        }

        override fun onLost(network: Network) {
            _isOnline.tryEmit(false)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun start() {
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Some devices might throw; best-effort
        }
    }

    fun stop() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            // ignore
        }
    }
}