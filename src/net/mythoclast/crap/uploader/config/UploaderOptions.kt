@file:Suppress("unused")

package net.mythoclast.crap.uploader.config

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

data class UploaderOptions(
    val username: String,
    val password: String,
    val comicId: Int,
    val items: List<UploadItem>
) {
    companion object {
        private var instance: UploaderOptions? = null
        val loginUrl: Url by lazy { generateUrl("/login.php") }
        val uploadUrl: Url by lazy {
            generateUrl("/managecomic.php",
                ParametersBuilder().also {
                    it.append("id", get().comicId.toString())
                    it.append("action", "uploadcomic")
                }
            )
        }
        fun get(): UploaderOptions {
            return if (null != instance) {
                instance!!
            } else {
                ConfigFactory.load().extract<UploaderOptions>().also {
                    if (null == instance) {
                        instance = it
                    }
                }
            }
        }
    }
    init {
        if (null != instance) {
            throw IllegalStateException("Uploader options cannot be loaded twice.")
        }
        instance = this
    }
    data class UploadItem(
        val title: String,
        val dateTime: UploadDateTime,
        val comicType: ComicType = ComicType.UPLOAD,
        val pathToImage: String,
        val description: String? = null,   // Mouseover text
        val urlToImage: String? = null,
        val htmlEmbedContent: String? = null,
        val authorComment: String? = null,
        val keywords: List<String> = listOf(),
        val transcript: String? = null
    ) {
        data class UploadDateTime(
            val day: Int,
            val month: Int,
            val year: Int,
            val hour: Int? = 12,
            val minute: Int? = 0,
            val second: Int? = 0
        )
        enum class ComicType(val id: String) {
            UPLOAD("1"),
            URL("2"),
            HTML_EMBED("3")
        }
    }
}

fun generateUrl(encodedPath: String = "/", parameters: ParametersBuilder = ParametersBuilder()): Url {
    return URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = "comicfury.com",
        encodedPath = encodedPath,
        parameters = parameters
    ).build()
}
