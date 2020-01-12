package net.mythoclast.crap.uploader

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.features.UserAgent
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.cookies.get
import io.ktor.client.features.cookies.cookies
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import net.mythoclast.crap.uploader.config.UploaderOptions
import net.mythoclast.crap.uploader.config.httpRequestConfigProvider
import java.io.File

@KtorExperimentalAPI
fun main() = runBlocking {
    HttpClient(
        CIO
    ) {

        // In this block we're basically saying
        // "don't slam the remote server with hundreds of concurrent requests,
        // keep timeouts turned on but high, enable a basically limitless amount of retries"
        //
        // That seems to be the ticket with this place, they like dropping your connection for all kinds of dumb reasons
        engine {
            maxConnectionsCount = 3
            endpoint {
                maxConnectionsPerRoute = 2
                pipelineMaxSize = 20
                keepAliveTime = 10000
                connectTimeout = 10000
                connectRetryAttempts = 5000
            }
        }


        install(Logging) { level = LogLevel.INFO }

        // We need to be able to log in, as the token this grants is required to upload content
        install(HttpCookies) { storage = AcceptAllCookiesStorage() }

        // We want to pretend to be Chrome to keep things easy,
        // I'm not interesting in being a good internet neighbor today
        install(UserAgent) {
            agent = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "AppleWebKit/537.36 (KHTML, like Gecko)",
                "Chrome/79.0.3945.117 Safari/537.36"
            ).joinToString(" ")
        }
    }.run {
        UploaderOptions.get().run {
            // This is called any time we need to "log back in"
            // It resets the login cookie, or sets it if it has not yet been
            suspend fun login() {
                submitForm<String>(
                    parametersOf(
                        Pair("username", listOf(username)),
                        Pair("password", listOf(password)),
                        Pair("stay", listOf("1"))
                    ),
                    false,
                    httpRequestConfigProvider(UploaderOptions.loginUrl, false)
                )
            }

            items.forEachIndexed { index, it ->
                // See, we have to be able to re-log-in because every 100 actions they just start ignoring you
                // presumably this is the token expiring and its use is limited based on how many things you do?
                // Their own bulk uploader is limited to 100 uploads per batch. I have to assume this same behavior
                // is the culprit.
                // Seems a bit odd but, sure, fine, whatever.
                // We just refresh the token every 50 cycles to keep well clear of the limit
                // Combine this with the aggressive retry policy and you, too, can use far more system resources
                // than were budgeted for you! Great!
                if (index % 50 == 0) {
                    login()
                }

                // Bruh
                //
                // So this is a giant enemy multipart-form
                // You must submit every key or the site returns a 200-OK without doing anything
                // Yep, its one of those.
                //
                // Coincidentally (ironically?) this has forced me to include literally every field you can submit
                // on the *normal* upload screen. Meaning this is a bulk uploader that's *more* capable
                // than either of their bulk or non-bulk uploaders. That I put together while between Destiny matches.
                submitFormWithBinaryData<String>(
                    formData(
                        FormPart("token", cookies(UploaderOptions.loginUrl)["token"]?.value ?: ""),
                        FormPart("title", it.title),
                        FormPart("description", it.description ?: ""),
                        FormPart("uploadtype", it.comicType.id), // "2" enabled comicurl, "3" enables embedhtml
                        File(it.pathToImage).let { image ->
                            FormPart(
                                // If you don't add this filename parameter it just tell you that you didn't upload a
                                // file and that payload sent over doesn't exist and actually never existed
                                // I ask you for a hamburger. You attempt to scream, but have no mouth.
                                "comic; filename=${image.name}",
                                image.readBytes(),
                                Headers.build {
                                    append(
                                        HttpHeaders.ContentType,
                                        when (image.extension) {
                                            "png" -> ContentType.Image.PNG
                                            "gif" -> ContentType.Image.GIF
                                            "jpg" -> ContentType.Image.JPEG
                                            "jpeg" -> ContentType.Image.JPEG
                                            else -> ContentType.Any
                                        }
                                    )
                                }
                            )
                        },
                        FormPart("comicurl", it.urlToImage ?: ""),
                        FormPart("embedhtml", it.htmlEmbedContent ?: ""),
                        FormPart("authcomment", it.authorComment ?: ""),
                        FormPart("day", it.dateTime.day),
                        FormPart("month", it.dateTime.month),
                        FormPart("year", it.dateTime.year),
                        FormPart("hour", it.dateTime.hour ?: "12"),
                        FormPart("minute", it.dateTime.minute ?: "0"),
                        FormPart("second", it.dateTime.second ?: "0"),
                        FormPart("keywords", it.keywords.joinToString(", ")),
                        FormPart("transcript", it.transcript ?: "")
                    ),
                    httpRequestConfigProvider(UploaderOptions.uploadUrl)
                )
            }
        }
    }
}
