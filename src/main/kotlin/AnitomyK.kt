package com.github.TraceLTRC

/**
 * A class for parsing anime video filenames.
 *
 * @property elements The container that holds all parsed elements from the filename. Acts like a [List]
 * @property tokens The container that holds all unparsed elements, after being split and identified. Acts like a [List]
 */
class AnitomyK {
    val elements = mutableListOf<Element>()
    val tokens = mutableListOf<Token>()

    /**
     * Parses a video filename and inserts each identified elements into [elements].
     * [elements] and [tokens] are cleared on each call to this function.
     *
     * Whether the parsing is successful or not, [elements] may still contain identified strings.
     *
     * @param filename the string filename
     * @param options [options][Options] for parsing
     * @return whether the parsing was successful or not.
     */
    fun parse(filename: String, options: Options = Options()): Boolean {
        elements.clear()
        tokens.clear()

        val mutFilename = StringBuilder(filename)

        if (options.parseFileExtension) {
            val extension = StringBuilder()
            if (removeExtensionFromFilename(mutFilename, extension)) {
                elements.add(ElementCategory.kElementFileExtension to extension.toString())
            }
        }

        if (options.ignored_strings.isNotEmpty()) {
            removeIgnoredStrings(mutFilename, options.ignored_strings)
        }

        if (filename.isEmpty()) {
            return false
        }
        elements.add(Pair(ElementCategory.kElementFileName, mutFilename.toString()))

        val tokenizer = Tokenizer(mutFilename.toString(), elements, options, tokens)
        if (!tokenizer.tokenize())
            return false

        val parser = Parser(elements, options, tokens)
        if (!parser.parse())
            return false

        return true
    }

    private fun removeExtensionFromFilename(filename: StringBuilder, extension: StringBuilder): Boolean {
        val position = filename.lastIndexOf('.')
        if (position == -1) return false

        extension.clear()
        extension.append(filename.substring(position + 1))

        if (extension.length > 4) return false // Hardcoded in Anitomy
        if (!extension.all { it.isLetterOrDigit() }) return false

        val keyword = KeywordManager.normalize(extension.toString())
        KeywordManager.find(ElementCategory.kElementFileExtension, keyword).let {
            if (it == null || it.category != ElementCategory.kElementFileExtension) return false
        }

        filename.setLength(position)

        return true
    }

    private fun removeIgnoredStrings(filename: StringBuilder, ignoredStrings: List<String>) {
        for (str in ignoredStrings) {
            var startIndex = filename.indexOf(str)
            while (startIndex != -1) {
                val endIndex = startIndex + str.length
                filename.replace(startIndex, endIndex, "")
                startIndex = filename.indexOf(str)
            }
        }
    }
}
