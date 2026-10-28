package com.firetv.immichslideshow.data

/**
 * Holds the API key for the shared OkHttp clients (REST + Coil). A single
 * application-wide client keeps connection pools warm across config changes.
 */
object AuthProvider {
    @Volatile
    var apiKey: String = ""
        private set

    fun set(key: String) {
        apiKey = key.trim()
    }
}
