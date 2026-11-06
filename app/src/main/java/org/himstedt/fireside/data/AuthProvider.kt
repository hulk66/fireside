package org.himstedt.fireside.data

/**
 * Holds the API key for the Coil image loader, which fetches thumbnails and is
 * built before any config exists. REST calls do not use this: a repository
 * authenticates from the [ImmichConfig] it was constructed with.
 */
object AuthProvider {
    @Volatile
    var apiKey: String = ""
        private set

    fun set(key: String) {
        apiKey = key.trim()
    }
}
