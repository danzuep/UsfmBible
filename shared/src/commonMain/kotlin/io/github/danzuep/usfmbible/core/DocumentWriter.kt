package io.github.danzuep.usfmbible.core

/** Writes a document the user picked, named by the same opaque platform reference as [DocumentReader]. */
fun interface DocumentWriter {
    /** Replaces the contents of the document at [ref]. Throws if it can't be opened. */
    fun writeBytes(ref: String, bytes: ByteArray)
}
