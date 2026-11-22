package com.example.potionsanddungeons

internal interface GamePreferences {
    fun values(): Map<String, String>
    fun put(changes: Map<String, Any?>)
}

internal class NetworkAccess(val denied: Boolean, val request: (() -> Unit) -> Unit)
