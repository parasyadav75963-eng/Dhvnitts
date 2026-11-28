package com.blindtechnexus.freeeloquencetts.pronunciation

enum class SpokenLanguage(val iso: String, val label: String) {

	HINDI("hi", "Hindi"),

	NEPALI("ne", "Nepali");

	companion object {

		fun of(iso: String?): SpokenLanguage = when (iso?.lowercase()) {
			"ne", "nep" -> NEPALI
			else -> HINDI
		}
	}
}
