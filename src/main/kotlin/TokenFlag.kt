package com.github.TraceLTRC

import java.util.EnumSet

typealias TokenFlags = EnumSet<TokenFlag>

fun TokenFlags.containsAny(flags: TokenFlags): Boolean {
    return EnumSet.copyOf(this).let {
        it.retainAll(flags)
        return@let it.isNotEmpty()
    }
}

enum class TokenCategory {
    kUnknown,
    kBracket,
    kDelimiter,
    kIdentifier,
    kInvalid
}

enum class TokenFlag {
    kFlagNone,
    kFlagBracket, kFlagNotBracket,
    kFlagDelimiter, kFlagNotDelimiter,
    kFlagIdentifier, kFlagNotIdentifier,
    kFlagUnknown, kFlagNotUnknown,
    kFlagValid, kFlagNotValid,

    kFlagEnclosed, kFlagNotEnclosed;

    companion object {
        val kFlagMaskCategories = EnumSet.of(
            kFlagBracket,
            kFlagDelimiter,
            kFlagIdentifier,
            kFlagUnknown,
            kFlagValid,
            kFlagNotBracket,
            kFlagNotDelimiter,
            kFlagNotIdentifier,
            kFlagNotUnknown,
            kFlagNotValid
        )
        val kFlagMaskEnclosed = EnumSet.of(kFlagEnclosed, kFlagNotEnclosed)
    }
}
