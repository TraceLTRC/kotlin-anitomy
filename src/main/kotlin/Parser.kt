package com.github.TraceLTRC

private const val ANIME_YEAR_MIN = 1900
private const val ANIME_YEAR_MAX = 2050
private const val EPISODE_NUMBER_MAX = ANIME_YEAR_MIN - 1
private const val VOLUME_NUMBER_MAX = 20

private val singleEpisodePattern = Regex("(\\d{1,4})[vV](\\d)")
private val multiEpisodePattern = Regex("(\\d{1,4})(?:[vV](\\d))?[-~&+](\\d{1,4})(?:[vV](\\d))?")
private val seasonAndEpisodePattern =
    Regex("S?(\\d{1,2})(?:-S?(\\d{1,2}))?(?:x|[ ._-x]?E)(\\d{1,4})(?:-E?(\\d{1,4}))?(?:[vV](\\d))?")
private val fractionalEpisodePattern = Regex("\\d+\\.5")
private val numberSignPattern = Regex("#(\\d{1,4})(?:[-~&+](\\d{1,4}))?(?:[vV](\\d))?")
private val japaneseCounterPattern = Regex("(\\d{1,4})\u8A71")

private val singleVolumePattern = Regex("(\\d{1,2})[vV](\\d)")
private val multiVolumePattern = Regex("(\\d{1,2})[-~&+](\\d{1,2})(?:[vV](\\d))?")

private val seperators: List<Pair<String, Boolean>> = listOf(
    "&" to true, "of" to true
)

internal class Parser(val elements: Elements, val options: Options, val tokens: MutableList<Token>) {

    private var foundEpisodeNumber = false

    fun parse(): Boolean {
        searchForKeywords()

        searchForIsolatedNumbers()

        if (options.parseEpisodeNumber)
            searchForEpisodeNumber()

        searchForAnimeTitle()

        if (options.parseReleaseGroup && elements.isEmpty(ElementCategory.kElementReleaseGroup))
            searchForReleaseGroup()

        if (options.parseEpisodeTitle && !elements.isEmpty(ElementCategory.kElementEpisodeNumber))
            searchForEpisodeTitle()

        validateElements()

        return !elements.isEmpty(ElementCategory.kElementAnimeTitle)
    }

    private fun searchForKeywords() {
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.category != TokenCategory.kUnknown) continue

            var word = token.content.trim(' ', '-')
            if (word.isEmpty()) continue
            // Don't bother if it's a number that's not CRC
            if (word.length != 8 && word.isNumeric()) continue

            val keyword = KeywordManager.normalize(word)
            var category = ElementCategory.kElementUnknown
            var options = KeywordOptions()

            val found = KeywordManager.find(category, keyword).let {
                if (it != null) {
                    category = it.category
                    options = it.options
                    return@let true
                }

                return@let false
            }

            if (found) {
                if (!this.options.parseReleaseGroup && category == ElementCategory.kElementReleaseGroup)
                    continue
                if (!isElementCategorySearchable(category) || !options.searchable)
                    continue
                if (isElementCategorySingular(category) && !this.elements.isEmpty(category))
                    continue
                if (category == ElementCategory.kElementAnimeSeasonPrefix) {
                    checkAnimeSeasonKeyword(index)
                    continue
                } else if (category == ElementCategory.kElementEpisodePrefix) {
                    if (options.valid)
                        checkExtentKeyword(ElementCategory.kElementEpisodeNumber, index)
                    continue
                } else if (category == ElementCategory.kElementReleaseVersion) {
                    word = word.substring(1) // number without "v"
                } else if (category == ElementCategory.kElementVolumePrefix) {
                    checkExtentKeyword(ElementCategory.kElementVolumeNumber, index)
                    continue
                }
            } else {
                if (elements.isEmpty(ElementCategory.kElementFileChecksum) && word.isCRC32()) {
                    category = ElementCategory.kElementFileChecksum
                } else if (elements.isEmpty(ElementCategory.kElementVideoResolution) &&
                    word.isResolution()
                ) {
                    category = ElementCategory.kElementVideoResolution
                }
            }

            if (category != ElementCategory.kElementUnknown) {
                elements.add(category to word)
                if (options.identifiable)
                    token.category = TokenCategory.kIdentifier
            }
        }
    }

    private fun searchForIsolatedNumbers() {
        for (tokenIndex in tokens.indices) {
            val token = tokens[tokenIndex]
            if (token.category != TokenCategory.kUnknown ||
                !token.content.isNumeric() ||
                !isTokenIsolated(tokenIndex)
            )
                continue

            val number = token.content.toInt()

            if (number in ANIME_YEAR_MIN..ANIME_YEAR_MAX) {
                if (elements.isEmpty(ElementCategory.kElementAnimeYear)) {
                    elements.add(ElementCategory.kElementAnimeYear to token.content)
                    token.category = TokenCategory.kIdentifier
                    continue
                }
            }

            if (number == 480 || number == 720 || number == 1080) {
                // If these numbers are isolated, it's more likely for them to be the
                // video resolution rather than the episode number. Some fansub groups
                // use these without the "p" suffix.
                if (elements.isEmpty(ElementCategory.kElementVideoResolution)) {
                    elements.add(ElementCategory.kElementVideoResolution to token.content)
                    token.category = TokenCategory.kIdentifier
                    continue
                }
            }
        }
    }

    private fun searchForEpisodeNumber() {
        val unknownTokenIndexes: MutableList<Int> = mutableListOf()
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.category == TokenCategory.kUnknown)
                if (token.content.findFirstInt() != -1)
                    unknownTokenIndexes.add(index)
        }

        if (unknownTokenIndexes.isEmpty())
            return

        foundEpisodeNumber = !elements.isEmpty(ElementCategory.kElementEpisodeNumber)

        // if a token matches a known episode pattern, it has to be the episode number
        if (searchForEpisodePatterns(unknownTokenIndexes))
            return

        if (!elements.isEmpty(ElementCategory.kElementEpisodeNumber))
            return // We have previously found on episode number via keywords

        // From now on, we're only interested in numeric tokens
        unknownTokenIndexes.removeIf { !tokens[it].content.isNumeric() }

        if (unknownTokenIndexes.isEmpty())
            return

        if (searchForEquivalentNumbers(unknownTokenIndexes))
            return

        if (searchForSeparatedNumbers(unknownTokenIndexes))
            return

        if (searchForIsolatedNumbers(unknownTokenIndexes))
            return


        searchforLastNumber(unknownTokenIndexes)
    }

    private fun searchForAnimeTitle() {
        var enclosedTitle = false

        // Find the first non-enclosed unknown token
        var tokenBegin = tokens.findToken(0, TokenFlags.of(TokenFlag.kFlagNotEnclosed, TokenFlag.kFlagUnknown))

        // If that doesn't work, find the first unknown token in the second enclosed
        // group, assuming that the first one is the release group
        if (tokenBegin == -1) {
            enclosedTitle = true
            tokenBegin = 0
            var skippedPreviousGroup = false
            while (true) {
                tokenBegin = tokens.findToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagUnknown))
                if (tokenBegin == -1)
                    break

                // Ignore groups that are composed of non-latin characters
                if (tokens[tokenBegin].content.isMostlyLatin())
                    if (skippedPreviousGroup)
                        break // found it

                // Get the first unknown token of the next group
                tokenBegin = tokens.findToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagBracket))
                tokenBegin = tokens.findToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagUnknown))
                skippedPreviousGroup = true
                if (tokenBegin == -1)
                    break
            }
        }
        if (tokenBegin == -1)
            return

        // Continue until an identifier (or a bracket, if the title is enclosed)
        // is found
        val flags =
            TokenFlags.of(TokenFlag.kFlagIdentifier, if (enclosedTitle) TokenFlag.kFlagBracket else TokenFlag.kFlagNone)
        var tokenEnd = tokens.findToken(tokenBegin, flags).let { if (it == -1) tokens.size else it }

        // If within the interval there's an open bracket without its matching pair,
        // move the upper endpoint back to the bracket
        if (!enclosedTitle) {
            var lastBracket = tokenEnd
            var bracketOpen = false
            for (tokenIndex in tokenBegin until tokenEnd) {
                if (tokens[tokenIndex].category == TokenCategory.kBracket) {
                    lastBracket = tokenIndex
                    bracketOpen = !bracketOpen
                }
            }
            if (bracketOpen)
                tokenEnd = lastBracket
        }

        // If the interval ends with an enclosed group (e.g. "Anime Title [Fansub]"),
        // move the upper endpoint back to the beginning of the group. We ignore
        // parentheses in order to keep certain groups (e.g. "(TV)") intact.
        if (!enclosedTitle) {
            var tokenIndex = tokens.findPreviousToken(tokenEnd, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
            while (tokenIndex != -1 &&
                tokens[tokenIndex].category == TokenCategory.kBracket &&
                tokens[tokenIndex].content.first() != ')'
            ) {
                tokenIndex = tokens.findPreviousToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagBracket))
                if (tokenIndex != -1) {
                    tokenEnd = tokenIndex
                    tokenIndex = tokens.findPreviousToken(tokenEnd, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
                }
            }
        }

        // Build Anime Title
        buildElement(ElementCategory.kElementAnimeTitle, false, tokenBegin, tokenEnd)
    }

    private fun searchForEpisodeTitle() {
        var tokenBegin = 0
        var tokenEnd = 0

        while (true) {
            // Find the first non-enclosed unknown token
            tokenBegin = tokens.findToken(tokenEnd, TokenFlags.of(TokenFlag.kFlagNotEnclosed, TokenFlag.kFlagUnknown))
            if (tokenBegin == -1)
                return

            tokenEnd = tokens.findToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagBracket, TokenFlag.kFlagIdentifier)).let {
                if (it == -1) tokens.size else it
            }

            // Ignore if it's only a dash
            if (tokenEnd - tokenBegin <= 2 && tokens[tokenBegin].content.isDashCharacter())
                continue

            buildElement(ElementCategory.kElementEpisodeTitle, false, tokenBegin, tokenEnd)
            return
        }
    }

    private fun validateElements() {
        // Validate anime type and episode title
        if (!elements.isEmpty(ElementCategory.kElementAnimeType) &&
            !elements.isEmpty(ElementCategory.kElementEpisodeTitle)) {
            // Here we check whether the episode title contains an anime type
            val episodeTitle = elements.get(ElementCategory.kElementEpisodeTitle)!!
            var index = 0
            while (index in elements.indices) {
                if (elements[index].first == ElementCategory.kElementAnimeType) {
                    if (episodeTitle.contains(elements[index].second)) {
                        if (episodeTitle.length == elements[index].second.length) {
                            // Invalid Episode Title
                            elements.removeIf { it.first == ElementCategory.kElementEpisodeTitle }
                        } else {
                            val keyword = KeywordManager.normalize(elements[index].second)
                            val found = KeywordManager.find(ElementCategory.kElementAnimeType, keyword).let {
                                return@let it != null && it.category == ElementCategory.kElementAnimeType
                            }
                            if (found) {
                                elements.removeAt(index) // Invalid anime type
                                continue
                            }
                        }
                    }
                }
                index++
            }
        }
    }

    private fun searchForReleaseGroup() {
        var tokenBegin = 0
        var tokenEnd = 0

        while (true) {
            // Find the first enclosed unknown token
            tokenBegin = tokens.findToken(tokenEnd, TokenFlags.of(TokenFlag.kFlagEnclosed, TokenFlag.kFlagUnknown))
            if (tokenBegin == -1)
                return

            // Continue until a bracket or identifier is found
            tokenEnd = tokens.findToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagBracket, TokenFlag.kFlagIdentifier))
            if (tokenEnd == -1 || tokens[tokenEnd].category != TokenCategory.kBracket)
                continue

            // Ignore if it's not the first non delimiter token in group
            val prevToken = tokens.findPreviousToken(tokenBegin, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
            if (prevToken != -1 && tokens[prevToken].category != TokenCategory.kBracket)
                continue

            // Build release group
            buildElement(ElementCategory.kElementReleaseGroup, true, tokenBegin, tokenEnd)
            return
        }
    }

    private fun buildElement(category: ElementCategory, keepDelimiters: Boolean, tokenBegin: Int, tokenEnd: Int) {
        var element = "" // AnitomyK: TODO: is string templates better?

        for (tokenIndex in tokenBegin until tokenEnd) {
            tokens[tokenIndex].let {
                when (it.category) {
                    TokenCategory.kUnknown -> {
                        element += it.content
                        it.category = TokenCategory.kIdentifier
                    }

                    TokenCategory.kBracket -> {
                        element += it.content
                    }

                    TokenCategory.kDelimiter -> {
                        val delimiter = it.content.first()
                        if (keepDelimiters) {
                            element += delimiter
                        } else if (tokenIndex != tokenBegin && tokenIndex != tokenEnd) {
                            when (delimiter) {
                                ',', '&' -> {
                                    element += delimiter
                                }

                                else -> {
                                    element += ' '
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }

        if (!keepDelimiters)
            element = element.trim(*StringConstants.dashesWithSpace.toCharArray())

        if (element.isNotEmpty())
            elements.add(category to element)
    }

    private fun searchForEquivalentNumbers(unknownTokenIndexes: List<Int>): Boolean {
        for (tokenIndex in unknownTokenIndexes) {
            val token = tokens[tokenIndex]
            if (isTokenIsolated(tokenIndex) || !token.content.isValidEpisodeNumber())
                continue

            // Find the first, enclosed, non-delimiter token
            var nextToken = tokens.findNextToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
            if (nextToken == -1 || tokens[nextToken].category != TokenCategory.kBracket)
                continue

            nextToken =
                tokens.findNextToken(nextToken, TokenFlags.of(TokenFlag.kFlagEnclosed, TokenFlag.kFlagNotDelimiter))
            if (nextToken == -1 || tokens[nextToken].category != TokenCategory.kUnknown)
                continue

            // Check if it's an isolated number
            if (!isTokenIsolated(nextToken) ||
                !tokens[nextToken].content.isNumeric() ||
                !tokens[nextToken].content.isValidEpisodeNumber()
            ) continue

            val minmax = minMax(token, tokens[nextToken])
            setEpisodeNumber(minmax.first.content, minmax.first, false)
            setAlternativeEpisodeNumber(minmax.second.content, minmax.second)

            return true
        }

        return false
    }

    private fun searchForSeparatedNumbers(unknownTokenIndexes: List<Int>): Boolean {
        for (unknownTokenIndex in unknownTokenIndexes) {
            val unknownToken = tokens[unknownTokenIndex]
            val prevToken = tokens.findPreviousToken(unknownTokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))

            if (prevToken != -1 &&
                tokens[prevToken].category == TokenCategory.kUnknown &&
                tokens[prevToken].content.isDashCharacter()
            ) {
                if (setEpisodeNumber(unknownToken.content, unknownToken, true)) {
                    tokens[prevToken].category = TokenCategory.kIdentifier
                    return true
                }
            }
        }

        return false
    }

    private fun searchForEpisodePatterns(unknownTokenIndexes: List<Int>): Boolean {
        for (index in unknownTokenIndexes) {
            val token = tokens[index]
            val numericFront = token.content.first().isDigit()

            if (!numericFront) {
                // e.g. "EP.1", "Vol.1"
                if (numberComesAfterPrefix(ElementCategory.kElementEpisodePrefix, token))
                    return true
                if (numberComesAfterPrefix(ElementCategory.kElementVolumePrefix, token))
                    continue
            } else {
                // e.g. "8 & 10", "01 of 24"
                if (numberComesBeforePrefix(token))
                    return true
            }
            // Look for other patterns
            if (matchEpisodePatterns(token.content, token))
                return true
        }

        return false
    }

    private fun searchForIsolatedNumbers(unknownTokenIndexes: List<Int>): Boolean {
        for (unknownTokenIndex in unknownTokenIndexes) {
            val unknownToken = tokens[unknownTokenIndex]

            if (!unknownToken.enclosed || !isTokenIsolated(unknownTokenIndex))
                continue

            if (setEpisodeNumber(unknownToken.content, unknownToken, true))
                return true
        }

        return false
    }

    private fun searchforLastNumber(unknownTokenIndexes: List<Int>): Boolean {
        for (unknownTokenIndex in unknownTokenIndexes.asReversed()) {
            val unknownToken = tokens[unknownTokenIndex]

            // Assuming that episode number always comes after the title, first token
            // cannot be what we're looking for
            if (unknownTokenIndex == 0)
                continue

            // An enclosed token is unlikely to be the episode number at this point
            if (unknownToken.enclosed)
                continue

            // Ignore if it's the first non-enclosed, non-delimiter token
            if (tokens.slice(0 until unknownTokenIndex).all { it.enclosed || it.category == TokenCategory.kDelimiter })
                continue

            // Ignore if the previous token is "Movie" or "Part"
            val prevToken = tokens.findPreviousToken(unknownTokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
            if (prevToken != -1 && tokens[prevToken].category == TokenCategory.kUnknown) {
                if (tokens[prevToken].content == "Movie" || tokens[prevToken].content == "Part")
                    continue
            }

            // We'll use this number after all
            if (setEpisodeNumber(unknownToken.content, unknownToken, true))
                return true
        }

        return false
    }

    private fun checkAnimeSeasonKeyword(tokenIndex: Int): Boolean {
        val token = tokens[tokenIndex]
        fun setAnimeSeason(first: Token, second: Token, content: String) {
            this.elements.add(ElementCategory.kElementAnimeSeason to content)
            first.category = TokenCategory.kIdentifier
            second.category = TokenCategory.kIdentifier
        }

        val prevToken = tokens.findPreviousToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
        if (prevToken != -1) {
            val number = tokens[prevToken].content.fromOrdinalToNumber()
            if (number.isNotEmpty()) {
                setAnimeSeason(tokens[prevToken], token, number)
                return true
            }
        }

        val nextToken = tokens.findNextToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
        if (nextToken != -1) {
            val content = tokens[nextToken].content
            if (content.isNumeric()) {
                setAnimeSeason(token, tokens[nextToken], content)
                return true
            }
        }

        return false
    }

    private fun checkExtentKeyword(category: ElementCategory, tokenIndex: Int): Boolean {
        val nextToken = tokens.findNextToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
        if (nextToken != -1 && tokens[nextToken].category == TokenCategory.kUnknown) {
            if (tokens[nextToken].content.findFirstInt() == 0) {
                when (category) {
                    ElementCategory.kElementEpisodeNumber -> {
                        if (!matchEpisodePatterns(tokens[nextToken].content, tokens[nextToken]))
                            setEpisodeNumber(tokens[nextToken].content, tokens[nextToken], false)
                    }

                    ElementCategory.kElementVolumeNumber -> {
                        if (!matchVolumePatterns(tokens[nextToken].content, tokens[nextToken]))
                            setVolumeNumber(tokens[nextToken].content, tokens[nextToken], false)
                    }

                    else -> {}
                }
                tokens[tokenIndex].category = TokenCategory.kIdentifier
                return true
            }
        }

        return false
    }

    private fun matchEpisodePatterns(word: String, token: Token): Boolean {
        if (word.isNumeric()) return false

        val trimmedWord = word.trim(' ', '-')
        val numericFront = trimmedWord.first().isDigit()
        val numericBack = trimmedWord.last().isDigit()

        if (numericFront && numericBack) {
            // e.g. "01v2"
            if (matchSingleEpisodePattern(trimmedWord, token)) return true
            // e.g. "01-02", "03-05v2"
            if (matchMultiEpisodePattern(trimmedWord, token)) return true
        }
        if (numericBack) {
            // e.g. "2x01", "S01E03", "S01-02xE001-150"
            if (matchSeasonAndEpisodePattern(trimmedWord, token)) return true
        }
        if (!numericFront) {
            // e.g. "ED1", "OP4a", "OVA2"
            if (matchTypeAndEpisodePattern(trimmedWord, token)) return true
        }
        if (numericFront && numericBack) {
            // e.g. "07.5"
            if (matchFractionalEpisodePattern(word, token)) return true
        }
        if (numericFront && !numericBack) {
            // e.g. "4a", "111C"
            if (matchPartialEpisodePattern(word, token)) return true
        }
        if (numericBack) {
            // e.g. "#01", "#02-03v2"
            if (matchNumberSignPattern(word, token)) return true
        }
        if (numericFront) {
            // U+8A71 is used as counter for stories, episodes of TV series, etc.
            if (matchJapaneseCounterPattern(word, token)) return true
        }

        return false
    }


    private fun matchVolumePatterns(word: String, token: Token): Boolean {
        // All patterns contain at least one non-numeric character
        if (word.isNumeric()) return false

        val trimmedWord = word.trim(' ', '-')
        val numericFront = trimmedWord.first().isDigit()
        val numericBack = trimmedWord.last().isDigit()

        if (numericFront && numericBack) {
            if (matchSingleVolumePattern(trimmedWord, token)) return true
            if (matchMultiVolumePattern(trimmedWord, token)) return true
        }

        return false
    }


    private fun matchSingleVolumePattern(word: String, token: Token): Boolean {
        singleVolumePattern.matchEntire(word)?.let {
            setVolumeNumber(it.groupValues[1], token, false)
            elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[2])
            return true
        }

        return false
    }

    private fun matchMultiVolumePattern(word: String, token: Token): Boolean {
        multiVolumePattern.matchEntire(word)?.let {
            val lowerBound = it.groupValues[1]
            val upperBound = it.groupValues[2]
            if (lowerBound.toInt() < upperBound.toInt()) {
                if (setVolumeNumber(lowerBound, token, true)) {
                    setVolumeNumber(upperBound, token, false)
                    if (it.groupValues[3].isNotEmpty())
                        elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[3])
                    return true
                }
            }
        }

        return false
    }

    private fun matchSingleEpisodePattern(word: String, token: Token): Boolean {
        singleEpisodePattern.matchEntire(word)?.let {
            setEpisodeNumber(it.groupValues[1], token, false)
            elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[2])
            return true
        }

        return false
    }

    private fun matchMultiEpisodePattern(word: String, token: Token): Boolean {
        multiEpisodePattern.matchEntire(word)?.let {
            val lowerBound = it.groupValues[1]
            val upperBound = it.groupValues[3]

            // Avoid matching expressions such as "009-1" or "5-2"
            if (lowerBound.toIntOrElse(0) < upperBound.toIntOrElse(0)) {
                if (setEpisodeNumber(lowerBound, token, true)) {
                    setEpisodeNumber(upperBound, token, false)
                    if (it.groupValues[2].isNotEmpty())
                        elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[2])
                    if (it.groupValues[4].isNotEmpty())
                        elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[4])
                    return true
                }
            }
        }

        return false
    }

    private fun matchSeasonAndEpisodePattern(word: String, token: Token): Boolean {
        seasonAndEpisodePattern.matchEntire(word)?.let {
            if (it.groupValues[1].toIntOrNull() == 0)
                return false
            elements.add(ElementCategory.kElementAnimeSeason to it.groupValues[1])
            if (it.groupValues[2].isNotEmpty())
                elements.add(ElementCategory.kElementAnimeSeason to it.groupValues[2])
            setEpisodeNumber(it.groupValues[3], token, false)
            if (it.groupValues[4].isNotEmpty())
                setEpisodeNumber(it.groupValues[4], token, false)
            return true
        }

        return false
    }

    private fun matchTypeAndEpisodePattern(word: String, token: Token): Boolean {
        val numberBegin = word.findFirstInt()
        val prefix = word.substring(0, numberBegin)

        val category = ElementCategory.kElementAnimeType
        var options = KeywordOptions()

        val found = KeywordManager.find(category, KeywordManager.normalize(prefix)).let {
            if (it != null) {
                if (it.category != category) {
                    return@let false
                }
                options = it.options
                return@let true
            }

            return@let false
        }
        if (found) {
            elements.add(ElementCategory.kElementAnimeType to prefix)
            val number = word.substring(numberBegin)
            if (matchEpisodePatterns(number, token) || setEpisodeNumber(number, token, true)) {
                val index = tokens.indexOf(token)
                if (index != -1) {
                    // Split token (we do this last in order to avoid invalidating our
                    // token reference earlier)
                    token.content = number
                    tokens.add(
                        index,
                        Token(
                            prefix,
                            if (options.identifiable) TokenCategory.kIdentifier else TokenCategory.kUnknown,
                            token.enclosed
                        )
                    )
                }
                return true
            }
        }

        return false
    }

    private fun matchFractionalEpisodePattern(word: String, token: Token): Boolean {
        // We don't allow any fractional part other than ".5", because there are cases
        // where such a number is a part of the anime title (e.g. "Evangelion: 1.11",
        // "Tokyo Magnitude 8.0") or a keyword (e.g. "5.1").

        if (fractionalEpisodePattern.matches(word)) {
            if (setEpisodeNumber(word, token, true))
                return true
        }

        return false
    }

    private fun matchPartialEpisodePattern(word: String, token: Token): Boolean {
        val index = word.find { !it.isDigit() }
        val suffixLength = word.length - index

        fun isValidSuffix(char: Char): Boolean {
            return (char in 'A'..'C') || (char in 'a'..'c')
        }

        if (suffixLength == 1 && isValidSuffix(word[index])) {
            if (setEpisodeNumber(word, token, true)) {
                return true
            }
        }

        return false
    }

    private fun matchNumberSignPattern(word: String, token: Token): Boolean {
        if (word.first() != '#')
            return false

        numberSignPattern.matchEntire(word)?.let {
            if (setEpisodeNumber(it.groupValues[1], token, true)) {
                if (it.groupValues[2].isNotEmpty()) setEpisodeNumber(it.groupValues[2], token, false)
                if (it.groupValues[3].isNotEmpty()) elements.add(ElementCategory.kElementReleaseVersion to it.groupValues[3])
                return true
            }
        }

        return false
    }

    private fun matchJapaneseCounterPattern(word: String, token: Token): Boolean {
        if (word.last() != '\u8A71')
            return false

        japaneseCounterPattern.matchEntire(word)?.let {
            setEpisodeNumber(it.groupValues[1], token, false)
            return true
        }

        return false
    }

    private fun setEpisodeNumber(number: String, token: Token, validate: Boolean): Boolean {
        if (validate && !number.isValidEpisodeNumber()) return false

        token.category = TokenCategory.kIdentifier
        var category = ElementCategory.kElementEpisodeNumber

        if (foundEpisodeNumber) {
            for (element in elements) {
                if (element.first != ElementCategory.kElementEpisodeNumber) continue

                // The larger number gets to be the alternative one
                val condition = number.toIntOrElse(0) - element.second.toIntOrElse(0)
                if (condition > 0) {
                    category = ElementCategory.kElementEpisodeNumberAlt
                } else if (condition < 0) {
                    elements.replace(element, ElementCategory.kElementEpisodeNumberAlt to element.second)
                } else {
                    return false // No need to add the same number twice
                }
                break
            }
        }

        elements.add(category to number)
        return true
    }

    private fun setAlternativeEpisodeNumber(number: String, token: Token) {
        elements.add(ElementCategory.kElementEpisodeNumberAlt to number)
        token.category = TokenCategory.kIdentifier
    }

    private fun setVolumeNumber(number: String, token: Token, validate: Boolean): Boolean {
        if (validate && !number.isValidVolumeNumber())
            return false

        elements.add(ElementCategory.kElementVolumeNumber to number)
        token.category = TokenCategory.kIdentifier
        return true
    }

    private fun isTokenIsolated(tokenIndex: Int): Boolean {
        val prevToken = tokens.findPreviousToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
        if (prevToken == -1 || tokens[prevToken].category != TokenCategory.kBracket)
            return false

        val nextToken = tokens.findNextToken(tokenIndex, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
        return !(nextToken == -1 || tokens[prevToken].category != TokenCategory.kBracket)
    }

    fun numberComesAfterPrefix(category: ElementCategory, token: Token): Boolean {
        val numberBegin = token.content.findFirstInt()
        val prefix = KeywordManager.normalize(token.content.substring(0, numberBegin))

        KeywordManager.find(category, prefix)?.let {
            if (it.category != category) return@let

            val number = token.content.substring(numberBegin)
            when (category) {
                ElementCategory.kElementEpisodePrefix -> {
                    if (!matchEpisodePatterns(number, token))
                        setEpisodeNumber(number, token, false)
                    return true
                }

                ElementCategory.kElementVolumePrefix -> {
                    if (!matchVolumePatterns(number, token))
                        setVolumeNumber(number, token, false)
                    return true
                }

                else -> {}
            }
        }

        return false
    }

    fun numberComesBeforePrefix(token: Token): Boolean {
        val seperatorToken = tokens.findNextToken(tokens.indexOf(token), TokenFlags.of(TokenFlag.kFlagNotDelimiter))

        if (seperatorToken != -1) {
            for (seperator in seperators) {
                if (tokens[seperatorToken].content == seperator.first) {
                    val otherToken = tokens.findNextToken(seperatorToken, TokenFlags.of(TokenFlag.kFlagNotDelimiter))
                    if (otherToken != -1 && tokens[otherToken].content.isNumeric()) {
                        setEpisodeNumber(token.content, token, false)
                        if (seperator.second)
                            setEpisodeNumber(tokens[otherToken].content, tokens[otherToken], false)
                        tokens[seperatorToken].category = TokenCategory.kIdentifier
                        tokens[otherToken].category = TokenCategory.kIdentifier
                        return true
                    }
                }
            }
        }

        return false
    }
}

internal fun String.isValidEpisodeNumber(): Boolean {
    this.toIntC().let {
        return  it <= EPISODE_NUMBER_MAX
    }
}

internal fun String.isValidVolumeNumber(): Boolean {
    this.toIntC().let {
        return it <= VOLUME_NUMBER_MAX
    }
}
