package io.github.limuyang2.pdf.viewer.contract

/**
 * Decodes a checked-in `.pdf.base64` resource without requiring a
 * platform-specific Base64 API.
 */
internal fun decodePdfContractFixture(encoded: String): ByteArray {
    val input = encoded.filterNot(Char::isWhitespace)
    require(input.length % 4 == 0) { "Invalid Base64 fixture length" }

    val padding = input.takeLastWhile { it == '=' }.length
    require(padding <= 2) { "Invalid Base64 fixture padding" }
    val output = ByteArray(input.length / 4 * 3 - padding)
    var outputIndex = 0

    input.chunked(4).forEach { chunk ->
        val bits =
            chunk.fold(0) { accumulator, character ->
                val value =
                    if (character == '=') {
                        0
                    } else {
                        base64Alphabet.indexOf(character).also {
                            require(it >= 0) {
                                "Invalid Base64 fixture character: $character"
                            }
                        }
                    }
                (accumulator shl 6) or value
            }

        if (outputIndex < output.size) {
            output[outputIndex++] = (bits shr 16).toByte()
        }
        if (outputIndex < output.size) {
            output[outputIndex++] = (bits shr 8).toByte()
        }
        if (outputIndex < output.size) {
            output[outputIndex++] = bits.toByte()
        }
    }
    return output
}

private const val base64Alphabet =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
