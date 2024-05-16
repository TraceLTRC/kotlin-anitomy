import kotlinx.serialization.json.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import com.github.TraceLTRC.*
import kotlin.test.*


data class UnitTest(val elements: Elements, var options: Options, val fileName: String)


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AnitomyKTest {

    companion object {
        @JvmStatic
        fun setupTestParameter(): List<Arguments> {
            val dataTests = mutableListOf<UnitTest>()

            val jsonTests = AnitomyKTest::class.java.getResource("data.json")!!.readText().let {
                Json.parseToJsonElement(it).jsonArray
            }
            for (testElement in jsonTests) {
                var fileName = ""
                val elements = mutableListOf<Element>()
                val ignoredStrings = mutableListOf<String>()
                var allowedDelimiters = " _.&+,|"

                val testObj = testElement.jsonObject
                for (key in testObj.keys) {
                    val elementCategory = key.toElementCategory()
                    if (elementCategory != null) {
                        // Check for array
                        if (testObj[key]!! is JsonArray) {
                            testObj[key]!!.jsonArray.forEach {
                                elements.add(elementCategory to it.jsonPrimitive.content)
                            }
                        } else { // Its a primitive
                            elements.add(elementCategory to testObj[key]!!.jsonPrimitive.content)
                        }
                    }

                    if (key == "file_name") {
                        fileName = testObj[key]!!.jsonPrimitive.content
                    }

                    if (key == "option_ignored_strings") {
                        testObj[key]!!.jsonArray.forEach {
                            ignoredStrings.add(it.jsonPrimitive.content)
                        }
                    }

                    if (key == "option_allowed_delimiters") {
                        allowedDelimiters = testObj[key]!!.jsonPrimitive.content
                    }
                }

                dataTests.add(
                    UnitTest(
                        elements,
                        Options(allowed_delimiters = allowedDelimiters, ignored_strings = ignoredStrings),
                        fileName
                    )
                )
            }
            return dataTests.map { Arguments.of(it) }
        }

        @JvmStatic
        fun setupFailingTestParameter(): List<Arguments> {
            val dataTests = mutableListOf<UnitTest>()

            val jsonTests = AnitomyKTest::class.java.getResource("failing_data.json")!!.readText().let {
                Json.parseToJsonElement(it).jsonArray
            }
            // TODO: Should extract this logic
            for (testElement in jsonTests) {
                var fileName = ""
                val elements = mutableListOf<Element>()
                val ignoredStrings = mutableListOf<String>()
                var allowedDelimiters = " _.&+,|"

                val testObj = testElement.jsonObject
                for (key in testObj.keys) {
                    val elementCategory = key.toElementCategory()
                    if (elementCategory != null) {
                        // Check for array
                        if (testObj[key]!! is JsonArray) {
                            testObj[key]!!.jsonArray.forEach {
                                elements.add(elementCategory to it.jsonPrimitive.content)
                            }
                        } else { // Its a primitive
                            elements.add(elementCategory to testObj[key]!!.jsonPrimitive.content)
                        }
                    }

                    if (key == "file_name") {
                        fileName = testObj[key]!!.jsonPrimitive.content
                    }

                    if (key == "option_ignored_strings") {
                        testObj[key]!!.jsonArray.forEach {
                            ignoredStrings.add(it.jsonPrimitive.content)
                        }
                    }

                    if (key == "option_allowed_delimiters") {
                        allowedDelimiters = testObj[key]!!.jsonPrimitive.content
                    }
                }

                dataTests.add(
                    UnitTest(
                        elements,
                        Options(allowed_delimiters = allowedDelimiters, ignored_strings = ignoredStrings),
                        fileName
                    )
                )
            }
            return dataTests.map { Arguments.of(it) }
        }
    }

    @ParameterizedTest
    @MethodSource("setupTestParameter")
    fun testParse(expected: UnitTest) {
        val anitomyK = AnitomyK()
        val success = anitomyK.parse(expected.fileName, expected.options)

        assertTrue(success)
        for (category in enumValues<ElementCategory>()) {
            if (category == ElementCategory.kElementFileName)
                // AnitomyK: The file_name element is missing the extension, but that
                // is intended
                continue

            val expectedStrings = expected.elements.getAll(category)
            val actualStrings = anitomyK.elements.getAll(category)
            assertEquals(expectedStrings.size, actualStrings.size)
            assertTrue(expectedStrings.containsAll(actualStrings))
        }
    }

    @ParameterizedTest
    @MethodSource("setupFailingTestParameter")
    fun testFailedParse(expected: UnitTest) {
        val anitomyK = AnitomyK()
        val success = anitomyK.parse(expected.fileName, expected.options)

        assertFails {
            assertTrue(success)
            for (category in enumValues<ElementCategory>()) {
                if (category == ElementCategory.kElementFileName)
                    continue

                val expectedStrings = expected.elements.getAll(category)
                val actualStrings = anitomyK.elements.getAll(category)
                assertEquals(expectedStrings.size, actualStrings.size)
                assertTrue(expectedStrings.containsAll(actualStrings))
            }
        }
    }
}



private fun String.toElementCategory(): ElementCategory? {
    return when (this) {
        "source" -> ElementCategory.kElementSource
        "language" -> ElementCategory.kElementLanguage
        "video_term" -> ElementCategory.kElementVideoTerm
        "episode_number_alt" -> ElementCategory.kElementEpisodeNumberAlt
        "episode_title" -> ElementCategory.kElementEpisodeTitle
        "anime_title" -> ElementCategory.kElementAnimeTitle
        "file_extension" -> ElementCategory.kElementFileExtension
        "audio_term" -> ElementCategory.kElementAudioTerm
        "release_group" -> ElementCategory.kElementReleaseGroup
        "file_name" -> ElementCategory.kElementFileName
        "file_checksum" -> ElementCategory.kElementFileChecksum
        "anime_season" -> ElementCategory.kElementAnimeSeason
        "anime_type" -> ElementCategory.kElementAnimeType
        "anime_year" -> ElementCategory.kElementAnimeYear
        "episode_number" -> ElementCategory.kElementEpisodeNumber
        "video_resolution" -> ElementCategory.kElementVideoResolution
        "subtitles" -> ElementCategory.kElementSubtitles
        "other" -> ElementCategory.kElementOther
        "release_information" -> ElementCategory.kElementReleaseInformation
        "release_version" -> ElementCategory.kElementReleaseVersion
        "volume_number" -> ElementCategory.kElementVolumeNumber
        else -> null
    }
}