package me.eroi.lolidaily.muzei.api

import android.webkit.CookieManager

object WebCookieStore {
    private const val EXPIRED_ATTRIBUTES =
        "Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"

    fun clearDomains(domains: List<String>) {
        val cookieManager = CookieManager.getInstance()
        domains.forEach { rawDomain ->
            val domain = rawDomain.trim().trimStart('.')
            if (domain.isEmpty()) return@forEach
            val url = "https://$domain/"
            cookieNames(cookieManager.getCookie(url)).forEach { name ->
                cookieManager.setCookie(url, "$name=; $EXPIRED_ATTRIBUTES")
                cookieManager.setCookie(url, "$name=; Domain=.$domain; $EXPIRED_ATTRIBUTES")
            }
        }
        cookieManager.flush()
    }

    internal fun cookieNames(cookieHeader: String?): Set<String> {
        if (cookieHeader.isNullOrBlank()) return emptySet()
        return cookieHeader
            .split(';')
            .mapNotNullTo(linkedSetOf()) { cookie ->
                cookie
                    .takeIf { it.contains('=') }
                    ?.substringBefore('=')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
    }
}
