package com.firetv.immichslideshow.data

/**
 * Connection details for a self-hosted Immich instance.
 *
 * @param baseUrl e.g. `http://192.168.1.20:2283` (a trailing slash is optional)
 * @param apiKey  an Immich API key created under Account Settings → API Keys
 * @param demo    when true no server is contacted; locally generated art is used
 */
data class ImmichConfig(
    val baseUrl: String,
    val apiKey: String,
    val demo: Boolean = false,
) {
    /** Base URL with exactly one trailing slash, as Retrofit requires. */
    fun normalizedBaseUrl(): String {
        var url = baseUrl.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url = "$url/"
        return url
    }

    /** Human readable host, used in headers and error messages. */
    fun displayHost(): String = normalizedBaseUrl()
        .removePrefix("http://")
        .removePrefix("https://")
        .trimEnd('/')

    fun sanitized(): ImmichConfig = copy(
        baseUrl = normalizedBaseUrl().trimEnd('/'),
        apiKey = apiKey.trim(),
    )

    companion object {
        val Demo = ImmichConfig(baseUrl = "demo://local", apiKey = "", demo = true)

        fun isValid(baseUrl: String, apiKey: String): Boolean =
            baseUrl.trim().isNotEmpty() && apiKey.trim().isNotEmpty()
    }
}
