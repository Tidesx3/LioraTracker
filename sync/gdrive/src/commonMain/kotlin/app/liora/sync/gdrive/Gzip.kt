package app.liora.sync.gdrive

/** Batches are JSON, which gzip shrinks to a fraction: the user's Drive space is theirs. */
internal expect fun gzip(bytes: ByteArray): ByteArray

internal expect fun gunzip(bytes: ByteArray): ByteArray
