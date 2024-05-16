package com.github.TraceLTRC

import java.util.EnumSet

/**
 * An identified string with the meaning of an element category. Acts like a [Pair],
 * with the first property being the category its signifying
 * (i.e an [anime title][ElementCategory.kElementAnimeTitle])
 * and the second property being the content
 */
typealias Element = Pair<ElementCategory, String>

/**
 * A [mutable list][MutableList] containing [elements][Element].
 */
typealias Elements = MutableList<Element>

/**
 * An enum listing all parse-able categories
 */
enum class ElementCategory {
    kElementAnimeSeason,
    kElementAnimeSeasonPrefix,
    kElementAnimeTitle,
    kElementAnimeType,
    kElementAnimeYear,
    kElementAudioTerm,
    kElementDeviceCompatibility,
    kElementEpisodeNumber,
    kElementEpisodeNumberAlt,
    kElementEpisodePrefix,
    kElementEpisodeTitle,
    kElementFileChecksum,
    kElementFileExtension,
    kElementFileName,
    kElementLanguage,
    kElementOther,
    kElementReleaseGroup,
    kElementReleaseInformation,
    kElementReleaseVersion,
    kElementSource,
    kElementSubtitles,
    kElementVideoResolution,
    kElementVideoTerm,
    kElementVolumeNumber,
    kElementVolumePrefix,
    kElementUnknown;

    internal companion object {
        internal val searchables = EnumSet.of(
            kElementAnimeSeasonPrefix,
            kElementAnimeType,
            kElementAudioTerm,
            kElementDeviceCompatibility,
            kElementEpisodePrefix,
            kElementFileChecksum,
            kElementLanguage,
            kElementOther,
            kElementReleaseGroup,
            kElementReleaseInformation,
            kElementReleaseVersion,
            kElementSource,
            kElementSubtitles,
            kElementVideoResolution,
            kElementVideoTerm,
            kElementVolumePrefix,
        )

        internal val singulars = EnumSet.complementOf(EnumSet.of(
            kElementAnimeSeason,
            kElementAnimeType,
            kElementAudioTerm,
            kElementDeviceCompatibility,
            kElementEpisodeNumber,
            kElementLanguage,
            kElementOther,
            kElementReleaseInformation,
            kElementSource,
            kElementVideoTerm,
        ))
    }
}

/**
 * Checks if an element category does not exist in this element container
 *
 * @param category The [ElementCategory] to check
 */
fun Elements.isEmpty(category: ElementCategory): Boolean {
    return this.none { it.first == category }
}

/**
 * Searches for the string with the specified category
 *
 * If there are multiple strings identified with the same category, it'll return the first identified string.
 *
 * @param category The [ElementCategory] to search for in the container
 * @return The identified string with the matched category. Null if there is none.
 */
operator fun Elements.get(category: ElementCategory): String? {
    return find { it.first == category }?.second
}

/**
 * Returns all strings with the specified category.
 *
 * This function will always return a list.
 * If no string is found to have the passed category, then the list will be empty.
 *
 * @param category the [ElementCategory] to search for in the container
 * @return A list of strings with the matched category.
 */
fun Elements.getAll(category: ElementCategory): List<String> {
    return filter { it.first == category }.map { it.second }
}

internal fun Elements.replace(oldElement: Element, newElement: Element) {
    val pos = this.indexOf(oldElement)
    this[pos] = newElement
}

internal fun isElementCategorySearchable(category: ElementCategory): Boolean {
    return ElementCategory.searchables.contains(category)
}

internal fun isElementCategorySingular(category: ElementCategory): Boolean {
    return ElementCategory.singulars.contains(category)
}
