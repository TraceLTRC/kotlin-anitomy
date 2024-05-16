package com.github.TraceLTRC

data class KeywordOptions(val identifiable: Boolean = true, val searchable: Boolean = true, val valid: Boolean = true)

data class Keyword(val category: ElementCategory, val options: KeywordOptions)

internal object KeywordManager {
    private val fileExtensions: MutableMap<String, Keyword> = mutableMapOf()
    private val keys: MutableMap<String, Keyword> = mutableMapOf()
    private val peekEntries: Map<ElementCategory, List<String>> = mapOf(
        ElementCategory.kElementAudioTerm to listOf("Dual Audio"),
        ElementCategory.kElementVideoTerm to listOf("H264", "H.264", "h264", "h.264"),
        ElementCategory.kElementVideoResolution to listOf("480p", "720p", "1080p", "2160p"),
        ElementCategory.kElementSource to listOf("Blu-Ra y")
    )

    init {
        val optionsDefault = KeywordOptions()
        val optionsInvalid = KeywordOptions(true, true, false)
        val optionsUnidentifiable = KeywordOptions(false, true, true)
        val optionsUnidentifiableInvalid = KeywordOptions(false, true, false)
        val optionsUnidentifiableUnsearchable = KeywordOptions(false, false, true)

        add(ElementCategory.kElementAnimeSeasonPrefix, optionsUnidentifiable, listOf("SAISON", "SEASON"))

        add(
            ElementCategory.kElementAnimeType, optionsUnidentifiable, listOf(
                "GEKIJOUBAN", "MOVIE",
                "OAD", "OAV", "ONA", "OVA",
                "SPECIAL", "SPECIALS",
                "TV"
            )
        )
        add(
            ElementCategory.kElementAnimeType,
            optionsUnidentifiableUnsearchable,
            listOf("SP") // e.g. "Yumeiro Patissiere SP Professional"
        )
        add(
            ElementCategory.kElementAnimeType, optionsUnidentifiableInvalid, listOf(
                "ED", "ENDING", "NCED",
                "NCOP", "OP", "OPENING",
                "PREVIEW", "PV"
            )
        )

        add(
            ElementCategory.kElementAudioTerm, optionsDefault, listOf(
                // Audio channels
                "2.0CH", "2CH", "5.1", "5.1CH", "7.1", "7.1CH", "DTS", "DTS-ES", "DTS5.1",
                "DOLBY TRUEHD", "TRUEHD", "TRUEHD5.1",
                // Audio codec
                "AAC", "AACX2", "AACX3", "AACX4", "AC3", "EAC3", "E-AC-3",
                "FLAC", "FLACX2", "FLACX3", "FLACX4", "LOSSLESS", "MP3", "OGG",
                "VORBIS",
                "ATMOS", "DOLBY ATMOS",
                // Audio language
                "DUALAUDIO", "DUAL AUDIO"
            )
        )
        add(
            ElementCategory.kElementAudioTerm, optionsUnidentifiable, listOf("OPUS")
        )

        add(
            ElementCategory.kElementDeviceCompatibility, optionsDefault, listOf(
                "IPAD3", "IPHONE5", "IPOD", "PS3", "XBOX", "XBOX360"
            )
        )
        add(
            ElementCategory.kElementDeviceCompatibility, optionsUnidentifiable, listOf(
                "ANDROID"
            )
        )

        add(
            ElementCategory.kElementEpisodePrefix, optionsDefault, listOf(
                "EP", "EP.", "EPS", "EPS.", "EPISODE", "EPISODE.", "EPISODES",
                "CAPITULO", "EPISODIO", "EPIS\u00F3DIO", "FOLGE"
            )
        )
        add(
            ElementCategory.kElementEpisodePrefix, optionsInvalid, listOf(
                "E", "\u7B2C"
            )
        )

        add(
            ElementCategory.kElementFileExtension, optionsDefault, listOf(
                "3GP", "AVI", "DIVX", "FLV", "M2TS", "MKV", "MOV", "MP4", "MPG",
                "OGM", "RM", "RMVB", "TS", "WEBM", "WMV"
            )
        )
        add(
            ElementCategory.kElementFileExtension, optionsInvalid, listOf(
                "AAC", "AIFF", "FLAC", "M4A", "MP3", "MKA", "OGG", "WAV", "WMA",
                "7Z", "RAR", "ZIP",
                "ASS", "SRT"
            )
        )

        add(
            ElementCategory.kElementLanguage, optionsDefault, listOf(
                "ENG", "ENGLISH", "ESPANOL", "JAP", "PT-BR", "SPANISH", "VOSTFR"
            )
        )
        add(
            ElementCategory.kElementLanguage, optionsUnidentifiable, listOf(
                "ESP", "ITA" // e.g. "Tokyo ESP", "Bokura ga Ita"
            )
        )

        add(
            ElementCategory.kElementOther, optionsDefault, listOf(
                "REMASTER", "REMASTERED", "UNCENSORED", "UNCUT",
                "TS", "VFR", "WIDESCREEN", "WS"
            )
        )

        add(ElementCategory.kElementReleaseGroup, optionsDefault, listOf("THORA"))

        add(
            ElementCategory.kElementReleaseInformation, optionsDefault, listOf(
                "BATCH", "COMPLETE", "PATCH", "REMUX"
            )
        )
        add(
            ElementCategory.kElementReleaseInformation, optionsUnidentifiable, listOf(
                "END", "FINAL" // e.g. "The End of Evangelion", "Final Approach"
            )
        )

        add(
            ElementCategory.kElementReleaseVersion, optionsDefault, listOf(
                "V0", "V1", "V2", "V3", "V4"
            )
        )

        add(
            ElementCategory.kElementSource, optionsDefault, listOf(
                "BD", "BDRIP", "BLURAY", "BLU-RAY",
                "DVD", "DVD5", "DVD9", "DVD-R2J", "DVDRIP", "DVD-RIP",
                "R2DVD", "R2J", "R2JDVD", "R2JDVDRIP",
                "HDTV", "HDTVRIP", "TVRIP", "TV-RIP",
                "WEBCAST", "WEBRIP"
            )
        )

        add(
            ElementCategory.kElementSubtitles, optionsDefault, listOf(
                "ASS", "BIG5", "DUB", "DUBBED", "HARDSUB", "HARDSUBS", "RAW",
                "SOFTSUB", "SOFTSUBS", "SUB", "SUBBED", "SUBTITLED",
                "MULTISUB", "MULTI SUB"
            )
        )

        add(
            ElementCategory.kElementVideoTerm, optionsDefault, listOf(
                // Frame rate
                "23.976FPS", "24FPS", "29.97FPS", "30FPS", "60FPS", "120FPS",
                // Video codec
                "8BIT", "8-BIT", "10BIT", "10BITS", "10-BIT", "10-BITS",
                "HI10", "HI10P", "HI444", "HI444P", "HI444PP",
                "HDR", "DV", "DOLBY VISION",
                "H264", "H265", "H.264", "H.265", "X264", "X265", "X.264",
                "AVC", "HEVC", "HEVC2", "DIVX", "DIVX5", "DIVX6", "XVID",
                "AV1",
                // Video format
                "AVI", "RMVB", "WMV", "WMV3", "WMV9",
                // Video quality
                "HQ", "LQ",
                // Video resolution
                "4K", "HD", "SD"
            )
        )

        add(
            ElementCategory.kElementVolumePrefix, optionsDefault, listOf(
                "VOL", "VOL.", "VOLUME"
            )
        )
    }

    fun add(category: ElementCategory, options: KeywordOptions, keywords: List<String>) {
        val keys = getKeywordContainer(category)
        for (keyword in keywords) {
            if (keyword.isEmpty()) continue
            if (keys.containsKey(keyword)) continue

            keys[keyword] = Keyword(category, options)
        }
    }

    fun find(category: ElementCategory, str: String): Keyword? {
        val keys = getKeywordContainer(category)
        val keyword = keys[str]
        if (keyword == null)
            return null

        return keyword
    }

    fun peek(filename: String, range: TokenRange, elements: Elements, preIdentifiedTokens: MutableList<TokenRange>) {
        for (entry in peekEntries) {
            for (keyword in entry.value) {
                val index = filename.indexOf(keyword, range.offset, range.offset + range.size)
                if (index != -1) {
                    elements.add(entry.key to keyword)
                    preIdentifiedTokens.add(TokenRange(index, keyword.length))
                }
            }
        }
    }

    fun normalize(str: String): String {
        return str.uppercase() // AnitomyK: Might be different from anitomy implementation.
    }

    private fun getKeywordContainer(category: ElementCategory): MutableMap<String, Keyword> {
        return if (category == ElementCategory.kElementFileExtension) {
            fileExtensions
        } else {
            keys
        }
    }
}