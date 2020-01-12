package net.mythoclast.crap.uploader.config

import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.toConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

// This big goofy thing converts pairs of titles and dates into UploadItems and maps them to their corresponding files
// The files were already sequentially numbered by release and matched the un-numbered sequence that had been found here
fun tryToNormalizeThisStuffIGuess() {
    // Get all the lines, skip any empties, if it is odd-numbered then it is not a date
    // If there's an odd number of lines then something is wrong with this file and why do I care this much?
    File("C:\\list.txt").readLines().filterNot { it.isBlank() }.let {
        if (it.size % 2 != 0) {
            return
        }
        // Anyway, we partition the titles and dates, resulting in two equally sized lists
        // This means that a title's date will be found at the same index in the other list
        it.withIndex().partition { value -> value.index % 2 == 0 }
    }.let {
        // Gotta pull the actual value back out of the IndexedValue
        it.first.map { idVal -> idVal.value }.zip(
            // The date needs even more processing! What luck!
            it.second.map {idVal ->
                // So the geniuses at comicfury.com store the date as separate field not only for day, month, and year,
                // but for hours, minutes, and seconds as well
                // because it's definitely not like there are numerous well established date solutions out there
                //
                // Their site is PHP though, we'll let them slide
                idVal.value.split('/').let { dateParts ->
                    UploaderOptions.UploadItem.UploadDateTime(
                        // The dates were written with the day in the second position
                        // but I put it first here just to make you cringe
                        dateParts[1].toInt(),
                        dateParts[0].toInt(),
                        // They're expecting 20 years after 2000, not the alleged death of Jesus
                        dateParts[2].toInt() + 2000
                    )
                }
            }
        ).mapIndexed { index, pair ->
            // And look at that you have the title, release date, and file path all wrapped up together
            UploaderOptions.UploadItem(
                pair.first,
                pair.second,
                pathToImage = trueImagePath(padCount(index))
            )
        }
    }.let {
        Files.writeString(
            // I didn't have permission to write to C:\
            // wew lad
            Path.of("D:\\normalized.conf"),

            // The config library can also convert custom config object back into their file representation,
            // that's what's happening here and we just don't have to look at that flat file anymore
            // there there
            UploaderOptions(
                "REPLACE",
                "REPLACE",
                -1,
                it
            ).toConfig("uploader").root().render(
                // Render in HOCON mode without comments because its easier to deal with
                ConfigRenderOptions.defaults().setOriginComments(false).setJson(false)
            ),
            Charsets.UTF_8
        )

    }
}

// There were only three extensions in use, sue me.
fun trueImagePath(padded: String): String = when {
    File("C:\\uploader\\images\\${padded}.jpg").exists() -> "C:\\uploader\\images\\${padded}.jpg"
    File("C:\\uploader\\images\\${padded}.gif").exists() -> "C:\\uploader\\images\\${padded}.gif"
    else -> "C:\\uploader\\images\\${padded}.png"
}

// l e f t  p a d
fun padCount(num: Int): String = (num + 443).toString().let {
    if (it.length >= 3) it else if (it.length == 2) "0$it" else "00$it"
}
