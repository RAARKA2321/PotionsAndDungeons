package com.example.potionsanddungeons

internal const val LAN_AVATAR_SIZE = 64
private const val PIXEL_COUNT = LAN_AVATAR_SIZE * LAN_AVATAR_SIZE
private const val HEX_DIGITS_PER_PIXEL = 8

internal class LanAvatar private constructor(val encoded: String) {
    fun pixels(): IntArray = IntArray(PIXEL_COUNT) { index ->
        val start = index * HEX_DIGITS_PER_PIXEL
        encoded.substring(start, start + HEX_DIGITS_PER_PIXEL).toUInt(16).toInt()
    }

    override fun equals(other: Any?): Boolean = other is LanAvatar && encoded == other.encoded
    override fun hashCode(): Int = encoded.hashCode()

    companion object {
        fun fromPixels(pixels: IntArray): LanAvatar {
            require(pixels.size == PIXEL_COUNT)
            return LanAvatar(buildString(PIXEL_COUNT * HEX_DIGITS_PER_PIXEL) {
                for (pixel in pixels) append(Integer.toHexString(pixel).padStart(HEX_DIGITS_PER_PIXEL, '0'))
            })
        }

        fun parse(value: String?): LanAvatar? {
            if (value == null || value.length != PIXEL_COUNT * HEX_DIGITS_PER_PIXEL) return null
            if (value.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
            return LanAvatar(value)
        }
    }
}
