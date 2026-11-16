package com.myapp.adventure.data.theme

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes an ARGB colour (stored as [Long] in `0xAARRGGBB` form) to a
 * human-readable hex string and back.
 *
 * Format:
 *  - Opaque colours (alpha = 0xFF) serialize as `"#RRGGBB"`.
 *  - Translucent colours serialize as `"#AARRGGBB"`.
 *  - Both `#RRGGBB` and `#AARRGGBB` are accepted when decoding.
 *
 * Rationale: theme JSON is user-editable and should be diff-friendly.
 * Storing the raw `Long` would produce `0xFF5D4037L`, which is unreadable
 * in a hand-edited file.
 */
object HexColorSerializer : KSerializer<Long> {

    override val descriptor =
        PrimitiveSerialDescriptor("HexColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        val alpha = ((value shr 24) and 0xFF).toInt()
        val rgb = value and 0xFFFFFF
        val hex = if (alpha == 0xFF) {
            "#%06X".format(rgb)
        } else {
            "#%02X%06X".format(alpha, rgb)
        }
        encoder.encodeString(hex)
    }

    override fun deserialize(decoder: Decoder): Long {
        val raw = decoder.decodeString().trim()
        require(raw.startsWith("#")) { "Hex colour must start with '#': $raw" }
        val hex = raw.substring(1)
        return when (hex.length) {
            6 -> 0xFF000000L or hex.toLong(16)              // #RRGGBB → opaque
            8 -> hex.toLong(16)                              // #AARRGGBB
            else -> error("Hex colour must be #RRGGBB or #AARRGGBB: $raw")
        }
    }
}