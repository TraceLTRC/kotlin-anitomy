package com.github.TraceLTRC

internal data class TokenRange(var offset: Int, var size: Int)

data class Token(
    var content: String,
    var category: TokenCategory = TokenCategory.kUnknown,
    val enclosed: Boolean = false
) {
    fun append(str: String) {
        this.content += str
    }
}

fun checkTokenFlags(token: Token, flags: TokenFlags): Boolean {
    if (flags.containsAny(TokenFlag.kFlagMaskEnclosed)) {
        val success = if (flags.contains(TokenFlag.kFlagEnclosed)) token.enclosed else !token.enclosed
        if (!success) return false
    }

    if (flags.containsAny(TokenFlag.kFlagMaskCategories)) {
        var success = false
        fun checkCategory(fe: TokenFlag, fn: TokenFlag, c: TokenCategory) {
            if (!success) {
                success = if (flags.contains(fe)) {
                    token.category == c
                } else if (flags.contains(fn)) {
                    token.category != c
                } else {
                    false
                }
            }
        }

        checkCategory(TokenFlag.kFlagBracket, TokenFlag.kFlagNotBracket, TokenCategory.kBracket)
        checkCategory(TokenFlag.kFlagDelimiter, TokenFlag.kFlagNotDelimiter, TokenCategory.kDelimiter)
        checkCategory(TokenFlag.kFlagIdentifier, TokenFlag.kFlagNotIdentifier, TokenCategory.kIdentifier)
        checkCategory(TokenFlag.kFlagUnknown, TokenFlag.kFlagNotUnknown, TokenCategory.kUnknown)
        checkCategory(TokenFlag.kFlagNotValid, TokenFlag.kFlagValid, TokenCategory.kInvalid)

        if (!success) return false
    }

    return true
}

fun List<Token>.findToken(startIndex: Int, flags: TokenFlags): Int {
    if (startIndex == -1) return -1

    for (index in startIndex until this.size) {
        if (checkTokenFlags(this[index], flags)) {
            return index
        }
    }

    return -1
}

fun List<Token>.findPreviousToken(startIndex: Int, flags: TokenFlags): Int {
    if (startIndex == -1) return -1

    for (index in startIndex - 1 downTo 0) {
        if (checkTokenFlags(this[index], flags)) {
            return index
        }
    }
    return -1
}

fun List<Token>.findNextToken(startIndex: Int, flags: TokenFlags): Int {
    if (startIndex == -1) return -1

    for (index in startIndex + 1 until this.size) {
        if (checkTokenFlags(this[index], flags)) {
            return index
        }
    }
    return -1
}

fun minMax(first: Token, second: Token): Pair<Token, Token> {
    val firstNum = first.content.toInt()
    val secondNum = second.content.toInt()

    if (firstNum <= secondNum) return first to second
    else return second to first
}