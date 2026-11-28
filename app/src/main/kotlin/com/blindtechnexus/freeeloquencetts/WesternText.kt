package com.blindtechnexus.freeeloquencetts

import java.text.Normalizer

object WesternText {

	private val PLAIN = mapOf(
		'‘' to "'", '’' to "'", '‚' to "'", '‛' to "'",
		'“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"",
		'‐' to "-", '‑' to "-", '‒' to "-", '–' to "-",
		'—' to "-", '―' to "-", '−' to "-",
		'…' to "...", '•' to ".", '·' to ".",
		' ' to " ", ' ' to " ", ' ' to " ", '​' to "",
		'‌' to "", '‍' to "", '﻿' to "",
		'‹' to "<", '›' to ">", '«' to "\"", '»' to "\"",
		'′' to "'", '″' to "\"", '⁄' to "/", '∕' to "/",
		'×' to "x", '™' to " trademark ", '№' to " number ",
		'€' to " euro ", '£' to " pounds ", '¥' to " yen ",
		'≤' to " less than or equal to ", '≥' to " greater than or equal to ",
		'≠' to " not equal to ", '≈' to " about ", '∞' to " infinity ",
		'→' to " to ", '←' to " from ", '°' to " degrees ",

		'œ' to "oe", 'Œ' to "OE", 'Ÿ' to "Y", 'ĳ' to "ij", 'Ĳ' to "IJ", 'ẞ' to "SS"
	)

	private const val HIGHEST = 0xFF

	private val MAPPED = BooleanArray(0x10000).apply {
		for (c in PLAIN.keys) this[c.code] = true
	}

	fun encode(text: String): ByteArray {
		return try {
			if (text.isEmpty()) return ByteArray(0)
			if (needsNoHelp(text)) encodeDirect(text) else encodeGeneral(text)
		} catch (broken: Throwable) {
			try {
				asciiFallback(text)
			} catch (failed: Throwable) {
				ByteArray(0)
			}
		}
	}

	private fun encodeDirect(text: String): ByteArray {
		var start = 0
		var end = text.length
		while (start < end && isSpaceCode(text[start].code)) start++
		while (end > start && isSpaceCode(text[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val direct = ByteArray(end - start)
		var o = 0
		var spaced = false
		var i = start
		while (i < end) {
			val code = text[i].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					direct[o++] = ' '.code.toByte()
					spaced = true
				}
			} else {
				direct[o++] = byteOf(code)
				spaced = false
			}
			i++
		}

		if (o == 1 && direct[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == direct.size) direct else direct.copyOf(o)
	}

	private fun encodeGeneral(text: String): ByteArray {
		val flat = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val c = text[i]
			val plain = if (c.code < MAPPED.size && MAPPED[c.code]) PLAIN[c] else null
			when {
				plain != null -> flat.append(plain)
				c.code == 0 -> flat.append(' ')
				c.code <= HIGHEST -> flat.append(c)
				else -> flat.append(fallback(text, i))
			}
			i += if (Character.isHighSurrogate(c) && i + 1 < text.length) 2 else 1
		}
		var start = 0
		var end = flat.length
		while (start < end && isSpaceCode(flat[start].code)) start++
		while (end > start && isSpaceCode(flat[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val out = ByteArray(end - start)
		var o = 0
		var spaced = false
		var j = start
		while (j < end) {
			val code = flat[j].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					out[o++] = ' '.code.toByte()
					spaced = true
				}
			} else {
				out[o++] = byteOf(code)
				spaced = false
			}
			j++
		}
		if (o == 1 && out[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == out.size) out else out.copyOf(o)
	}

	private fun byteOf(code: Int): Byte {
		if (code in 0x80..0x9F) return ' '.code.toByte()
		if (code in 1..HIGHEST) return code.toByte()
		return ' '.code.toByte()
	}

	private fun isSpaceCode(code: Int): Boolean =
		code == 0x20 || (code in 0x09..0x0D)

	private fun needsNoHelp(text: String): Boolean {
		for (at in text.indices) {
			val c = text[at]
			if (c.code > HIGHEST || c.code == 0 || MAPPED[c.code]) return false
		}
		return true
	}

	private fun fallback(text: String, at: Int): String {
		return try {
			val c = text[at]
			val one = if (Character.isHighSurrogate(c) && at + 1 < text.length) {
				text.substring(at, at + 2)
			} else {
				c.toString()
			}
			val stripped = Normalizer.normalize(one, Normalizer.Form.NFD)
				.filter { it.code <= HIGHEST && !isCombining(it) }
			if (stripped.isEmpty()) " " else stripped
		} catch (broken: Throwable) {
			" "
		}
	}

	private fun asciiFallback(text: String): ByteArray {
		var start = 0
		var end = text.length
		while (start < end && isSpaceCode(text[start].code)) start++
		while (end > start && isSpaceCode(text[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val out = ByteArray(end - start)
		var o = 0
		var spaced = false
		for (i in start until end) {
			val code = text[i].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					out[o++] = ' '.code.toByte()
					spaced = true
				}
			} else if (code in 1..0x7E) {
				out[o++] = code.toByte()
				spaced = false
			} else if (!spaced) {
				out[o++] = ' '.code.toByte()
				spaced = true
			}
		}
		if (o == 1 && out[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == out.size) out else out.copyOf(o)
	}

	private fun isCombining(c: Char): Boolean = when (Character.getType(c).toByte()) {
		Character.NON_SPACING_MARK,
		Character.COMBINING_SPACING_MARK,
		Character.ENCLOSING_MARK -> true
		else -> false
	}
}
