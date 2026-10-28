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

        /**
         * Addresses worth trying, most likely first: exactly what was typed, then
         * the same host with the other scheme. A LAN Immich is usually plain http
         * on :2283, a public one https on :443, and people rarely type a scheme.
         */
        fun addressCandidates(raw: String): List<String> {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return emptyList()
            return when {
                trimmed.startsWith("https://") ->
                    listOf(trimmed, "http://" + trimmed.removePrefix("https://"))
                trimmed.startsWith("http://") ->
                    listOf(trimmed, "https://" + trimmed.removePrefix("http://"))
                else -> listOf("https://$trimmed", "http://$trimmed")
            }
        }
    }
}
