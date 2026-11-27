package io.github.danzuep.usfmbible.core

/** Reads a document the user picked, named by an opaque platform reference (a content URI on Android). */
interface DocumentReader {
    /** Reads up to [limit] bytes of the document at [ref]. Throws if it can't be opened. */
    fun readBytes(ref: String, limit: Int = Int.MAX_VALUE): ByteArray
}
