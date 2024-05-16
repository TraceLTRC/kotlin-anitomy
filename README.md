# kotlin-anitomy

> kotlin-anitomy (or AnitomyK) is a Kotlin port of the C++ library [anitomy](https://github.com/erengy/anitomy).
> Anitomy is a C++ library for parsing anime video filenames. It's accurate, fast, and simple to use.
> All credit goes to the authors of the anitomy code.

## Installation

First, add the JitPack repo to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven { uri("https://jitpack.io") }
}
```

Then, add the dependency:

```kotlin
    implementation("com.github.TraceLTRC:kotlin-anitomy:v1.0")
```

## Examples

The following filename...

`[TaigaSubs]_Toradora!_(2008)_-_01v2_-_Tiger_and_Dragon_[1280x720_H.264_FLAC][1234ABCD].mkv`

...is resolved into these elements:

- Release group: *TaigaSubs*
- Anime title: *Toradora!*
- Anime year: *2008*
- Episode number: *01*
- Release version: *2*
- Episode title: *Tiger and Dragon*
- Video resolution: *1280x720*
- Video term: *H.264*
- Audio term: *FLAC*
- File checksum: *1234ABCD*

Here's an example code snippet

```kotlin
package org.example

import xyz.tracel.AnitomyK
import xyz.tracel.ElementCategory
import xyz.tracel.get

fun main() {
    val anitomyK = AnitomyK()
    val success = anitomyK.parse("[Ouroboros]_Fullmetal_Alchemist_Brotherhood_-_01.mkv")
    if (success) {                        
        val elements = anitomyK.elements
        for (element in elements) {
            println("${element.first} ${element.second}")
        }
        println()

        println("${elements.get(ElementCategory.kElementAnimeTitle)} " +
                "#${elements.get(ElementCategory.kElementEpisodeNumber)} by" +
                " ${elements.get(ElementCategory.kElementReleaseGroup)}")
    }
}
```

...which will output:

```
kElementFileExtension mkv
kElementFileName [Ouroboros]_Fullmetal_Alchemist_Brotherhood_-_01
kElementEpisodeNumber 01
kElementAnimeTitle Fullmetal Alchemist Brotherhood
kElementReleaseGroup Ouroboros

Fullmetal Alchemist Brotherhood #01 by Ouroboros
```

## Issues & PRs
Contributions are welcome. Though, currently this library only accepts PR that helps with maintaining the parity with anitomy.
This library does not try to add/remove any features/bugs that are or are not present in anitomy.
Documentation fixes or additions are greatly appreciated!