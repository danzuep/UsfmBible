package io.github.danzuep.usfmbible.ui

/** Compares language names alphabetically ignoring case, falling back to code point order so the result is stable. */
val languageNameComparator: Comparator<String> = Comparator { a, b ->
    val r = a.compareTo(b, ignoreCase = true)
    if (r != 0) r else a.compareTo(b)
}

/** Sorts [this] alphabetically by [name]. */
fun <T> Iterable<T>.sortedByLanguageName(name: (T) -> String): List<T> =
    sortedWith { a, b -> languageNameComparator.compare(name(a), name(b)) }
