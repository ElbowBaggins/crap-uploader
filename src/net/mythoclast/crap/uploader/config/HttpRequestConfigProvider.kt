package net.mythoclast.crap.uploader.config

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

fun httpRequestConfigProvider(url: Url, cookie: Boolean = true): HttpRequestBuilder.() -> Unit = {
    url(url)
    if (!cookie && headers.contains(HttpHeaders.Cookie)) {
        headers.remove(HttpHeaders.Cookie)
    }
}
