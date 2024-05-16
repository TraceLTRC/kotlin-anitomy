package com.github.TraceLTRC

/**
 * A class for setting the options during the parsing of a filename.
 *
 * @property parseEpisodeNumber whether AnitomyK should try and find an episode number
 * @property parseEpisodeTitle whether AnitomyK should try and find an episode title
 * @property parseFileExtension whether AnitomyK should try and find a file extension
 * @property parseReleaseGroup whether AnitomyK should try and find a release group
 * @property allowed_delimiters A string containing characters that AnitomyK considers a delimiter
 * @property ignored_strings A list of strings AnitomyK will discard before tokenizing
 */
data class Options(
    val parseEpisodeNumber: Boolean = true,
    val parseEpisodeTitle: Boolean = true,
    val parseFileExtension: Boolean = true,
    val parseReleaseGroup: Boolean = true,
    val allowed_delimiters: String = " _.&+,|",
    val ignored_strings: List<String> = emptyList()
)
