package com.github.TraceLTRC

internal class Tokenizer(
    val filename: String,
    val elements: MutableList<Pair<ElementCategory, String>>,
    val options: Options,
    val tokens: MutableList<Token>
) {
    companion object {
        val brackets = listOf(
            '(' to ')',
            '[' to ']',
            '{' to '}',
            '\u300C' to '\u300D',
            '\u300E' to '\u300F',
            '\u3010' to '\u3011',
            '\uFF08' to '\uFF09'
        )
    }

    fun tokenize(): Boolean {
        tokenizeByBracket()
        return tokens.isNotEmpty()
    }

    private fun tokenizeByBracket() {
        var matchingBracket = '\n'
        fun findFirstBracket(charBegin: Int, charEnd: Int): Int {
            return filename.find(charBegin, charEnd) {
                for (bracket in brackets) {
                    if (it == bracket.first) {
                        matchingBracket = bracket.second
                        return@find true
                    }
                }
                return@find false
            }
        }

        var isBracketOpen = false
        var index = 0
        while (index < filename.length) {
            val foundIndex = if (!isBracketOpen) {
                findFirstBracket(index, filename.length)
            } else {
                filename.indexOf(matchingBracket, index)
            }

            val range = TokenRange(index, (if (foundIndex == -1) filename.length else foundIndex) - index)
            if (range.size > 0) tokenizeByPreIdentified(isBracketOpen, range)

            if (foundIndex != -1) { // Found bracket
                addToken(TokenCategory.kBracket, true, TokenRange(foundIndex, 1))
                isBracketOpen = !isBracketOpen
                index = foundIndex + 1
            } else {
                break
            }
        }
    }

    private fun tokenizeByPreIdentified(enclosed: Boolean, range: TokenRange) {
        val preIdentifiedTokens: MutableList<TokenRange> = mutableListOf()
        KeywordManager.peek(filename, range, elements, preIdentifiedTokens)

        var index = range.offset
        val subrange = TokenRange(range.offset, 0)

        while (index < range.offset + range.size) {
            for (preIdentifiedToken in preIdentifiedTokens) {
                if (index == preIdentifiedToken.offset) {
                    if (subrange.size > 0) tokenizeByDelimiters(enclosed, subrange)

                    addToken(TokenCategory.kIdentifier, enclosed, preIdentifiedToken)
                    subrange.offset = preIdentifiedToken.offset + preIdentifiedToken.size
                    index = subrange.offset - 1 // It's going to be incremented below
                    break
                }
            }
            subrange.size = ++index - subrange.offset
        }

        if (subrange.size > 0) tokenizeByDelimiters(enclosed, subrange)
    }

    private fun tokenizeByDelimiters(enclosed: Boolean, range: TokenRange) {
        val delimiters = getDelimiters(range)

        if (delimiters.isEmpty()) {
            addToken(TokenCategory.kUnknown, enclosed, range)
            return
        }

        var index = range.offset
        while (index in range.offset until range.offset + range.size) {
            val foundIndex = filename.find(index, range.offset + range.size) {
                delimiters.contains(it)
            }

            val subrange = TokenRange(index, (if (foundIndex == -1) range.offset + range.size else foundIndex) - index)
            if (subrange.size > 0) addToken(TokenCategory.kUnknown, enclosed, subrange)

            if (foundIndex != -1) { // Found delimiter
                addToken(TokenCategory.kDelimiter, enclosed, TokenRange(foundIndex, 1))
                index = foundIndex + 1
            } else {
                break
            }
        }

        validateDelimiterTokens()
    }

    private fun getDelimiters(range: TokenRange): String {
        return filename.substring(range.offset, range.offset + range.size).run {
            options.allowed_delimiters.filter { this.contains(it) }
        }
    }

    private fun validateDelimiterTokens() {
        fun isDelimiterToken(index: Int): Boolean {
            return index != -1 && tokens[index].category == TokenCategory.kDelimiter
        }

        fun isUnknownToken(index: Int): Boolean {
            return index != -1 && tokens[index].category == TokenCategory.kUnknown
        }

        fun isSingleCharacterToken(index: Int): Boolean {
            return isUnknownToken(index) &&
                    tokens[index].content.length == 1 &&
                    tokens[index].content.first() != '-'
        }

        fun appendTokenTo(token: Token, appendTo: Token) {
            appendTo.append(token.content)
            token.category = TokenCategory.kInvalid
        }

        for (index in tokens.indices) {
            val token = tokens[index]

            if (token.category != TokenCategory.kDelimiter) continue

            val delimiter = token.content.first()
            val prevToken = tokens.findPreviousToken(index, TokenFlags.of(TokenFlag.kFlagValid))
            var nextToken = tokens.findNextToken(index, TokenFlags.of(TokenFlag.kFlagValid))

            if (delimiter != ' ' && delimiter != '_') {
                if (isSingleCharacterToken(prevToken)) {
                    appendTokenTo(token, tokens[prevToken])
                    while (isUnknownToken(nextToken)) {
                        appendTokenTo(tokens[nextToken], tokens[prevToken])
                        nextToken = tokens.findNextToken(nextToken, TokenFlags.of(TokenFlag.kFlagValid))
                        if (isDelimiterToken(nextToken) && tokens[nextToken].content.first() == delimiter) {
                            appendTokenTo(tokens[nextToken], tokens[prevToken])
                            nextToken = tokens.findNextToken(nextToken, TokenFlags.of(TokenFlag.kFlagValid))
                        }
                    }
                    continue
                }

                if (isSingleCharacterToken(nextToken)) {
                    appendTokenTo(token, tokens[prevToken])
                    appendTokenTo(tokens[nextToken], tokens[prevToken])
                    continue
                }
            }

            // Check for adjacent delimiters
            if (isUnknownToken(prevToken) && isDelimiterToken(nextToken)) {
                val nextDelimiter = tokens[nextToken].content.first()
                if (delimiter != nextDelimiter &&
                    delimiter != ',' &&
                    (nextDelimiter == ' ' || nextDelimiter == '_')
                ) {
                    appendTokenTo(token, tokens[prevToken])
                }
            } else if (isDelimiterToken(prevToken) && isDelimiterToken(nextToken)) {
                val prevDelimiter = tokens[prevToken].content.first()
                val nextDelimiter = tokens[nextToken].content.first()
                if (prevDelimiter == nextDelimiter && prevDelimiter != delimiter) {
                    token.category = TokenCategory.kUnknown // e.g. "&" in "_&_"
                }
            }

            // Check for other special cases
            if ((delimiter == '&' || delimiter == '+') &&
                isUnknownToken(prevToken) && isUnknownToken(nextToken) &&
                tokens[prevToken].content.isNumeric() &&
                tokens[nextToken].content.isNumeric()
            ) {
                appendTokenTo(token, tokens[prevToken])
                appendTokenTo(tokens[nextToken], tokens[prevToken])
            }
        }

        tokens.removeIf {
            it.category == TokenCategory.kInvalid
        }
    }

    private fun addToken(category: TokenCategory, enclosed: Boolean, range: TokenRange) {
        tokens.add(
            Token(
                filename.substring(range.offset, range.offset + range.size),
                category,
                enclosed
            )
        )
    }
}