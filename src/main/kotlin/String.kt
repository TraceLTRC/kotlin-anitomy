package com.github.TraceLTRC

internal object StringConstants {
    val ordinals = mapOf(
        "1st" to "1", "First" to "1",
        "2nd" to "2", "Second" to "2",
        "3rd" to "3", "Third" to "3",
        "4th" to "4", "Fourth" to "4",
        "5th" to "5", "Fifth" to "5",
        "6th" to "6", "Sixth" to "6",
        "7th" to "7", "Seventh" to "7",
        "8th" to "8", "Eighth" to "8",
        "9th" to "9", "Ninth" to "9"
    )
    
    const val dashes = "-\u2010\u2011\u2012\u2013\u2014\u2015"
    const val dashesWithSpace = "$dashes "
}

internal fun String.find(startIndex: Int = 0, endIndex: Int = this.length, predicate: (Char) -> Boolean): Int {
    require(startIndex in indices) { "Invalid start index" }
    require(endIndex <= this.length) { "invalid end index" }
    require(startIndex < endIndex) { "start index must be lower than end index" }

    for (index in startIndex until endIndex) {
        if (predicate(this[index])) {
            return index
        }
    }
    return -1
}

internal fun String.indexOf(string: String, startIndex: Int = 0, endIndex: Int = this.length): Int {
    require(startIndex in indices) { "Invalid start index" }
    require(endIndex <= this.length) { "invalid end index" }
    require(startIndex < endIndex) { "start index must be lower than end index" }

    for (index in startIndex until endIndex - string.length + 1) {
        var found = true
        for (subIndex in string.indices) {
            if (this[index + subIndex] != string[subIndex]) {
                found = false
                break
            }
        }
        if (found) {
            return index
        }
    }
    return -1
}

internal fun String.isNumeric(): Boolean {
    return this.all { it.isDigit() }
}

internal fun String.fromOrdinalToNumber(): String {
    StringConstants.ordinals[this].let {
        return it ?: ""
    }
}

/**
 * Finds the first occurrence of a digit in a string and returns the index of said digit.
 */
internal fun String.findFirstInt(): Int {
    for (index in indices) {
        if (this[index].isDigit()) return index
    }
    return -1
}

internal fun String.toIntOrElse(default: Int): Int {
    return this.toIntOrNull() ?: default
}

internal fun String.isCRC32(): Boolean {
    return this.length == 8 && this.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
}

internal fun String.isResolution(): Boolean {
    // Using a regex such as "\\d{3,4}(p|(x\\d{3,4}))$" would be more elegant,
    // but it's much slower (e.g. 2.4ms -> 24.9ms).
    // AnitomyK: Might be good to benchmark it for Kotlin

    val minWidthSize = 3
    val minHeightSize = 3

    // *###x###*
    if (length >= minWidthSize + 1 + minHeightSize) {
        val pos = this.find { "xX\u00D7".contains(it) } // multiplication sign
        if (pos != -1 && pos >= minWidthSize && pos <= length - (minHeightSize + 1)) {
            for (index in indices) {
                if (index != pos && !this[index].isDigit())
                    return false
            }
            return true
        }
    } else if (length >= minHeightSize +1) { // *###p
        if (last() == 'p' || last() == 'P') {
            for (index in 0 until length-1) {
                if (!this[index].isDigit())
                    return false
            }
            return true
        }
    }

    return false
}

internal fun String.isDashCharacter(): Boolean {
    return this.length == 1 && StringConstants.dashes.contains(this.first())
}

internal fun String.isMostlyLatin(): Boolean {
    val length: Double = if (this.isEmpty()) 1.0 else this.length.toDouble()
    return this.count { it < '\u024F' } / length >= 0.5
}

// Similar to C++'s wcstol
internal fun String.toIntC(): Int {
    var num = 0
    for (char in this) {
        if (char.isWhitespace()) continue
        if (!char.isDigit()) break

        num = (num * 10) + (char.code - '0'.code)
    }

    return num
}
