package com.blindtechnexus.freeeloquencetts.pronunciation

internal class Akshara {

	val onset = StringBuilder()

	var vowel: Devanagari.Vowel? = Devanagari.SCHWA

	var nasal: Char = '\u0000'

	var visarga: Boolean = false

	var independent: Boolean = false

	var writtenVowel: Boolean = false

	val isEmpty: Boolean get() = onset.isEmpty() && !independent && nasal == '\u0000' && !visarga

	val hasSchwa: Boolean get() = vowel?.schwa == true

	fun onsetText(): String = onset.toString()

	override fun toString(): String =
		"${onset}${vowel?.phoneme ?: "-"}${if (nasal == '\u0000') "" else "~"}"
}
