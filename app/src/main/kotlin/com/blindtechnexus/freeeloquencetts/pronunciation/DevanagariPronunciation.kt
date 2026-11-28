package com.blindtechnexus.freeeloquencetts.pronunciation

object DevanagariPronunciation {

	fun hasDevanagari(text: String): Boolean {
		for (c in text) {
			if (Devanagari.isDevanagari(c) && Devanagari.isLetter(c)) return true
		}
		return false
	}

	fun isDevanagariWord(word: String): Boolean = Syllabifier.hasDevanagari(word)

	fun apply(text: String, language: SpokenLanguage? = null): String {
		if (text.isEmpty() || !hasDevanagari(text)) return text
		val chosen = language ?: PronunciationRules.guessLanguage(text)
		val out = StringBuilder(text.length + 24)
		val word = StringBuilder(16)

		fun flushWord() {
			if (word.isEmpty()) return
			val said = try {
				PronunciationRules.pronounce(word.toString(), chosen)
			} catch (_: Throwable) {

				""
			}
			out.append(said)
			word.setLength(0)
		}

		var at = 0
		while (at < text.length) {
			val c = text[at]
			when {
				Devanagari.isLetter(c) -> word.append(c)
				c == Devanagari.ZERO_WIDTH_JOINER || c == Devanagari.ZERO_WIDTH_NON_JOINER ->
					if (word.isNotEmpty()) word.append(c)
				Devanagari.DIGITS.containsKey(c) -> {
					flushWord()
					out.append(Devanagari.DIGITS.getValue(c))
				}
				c == Devanagari.DANDA || c == Devanagari.DOUBLE_DANDA -> {

					flushWord()
					out.append(". ")
				}
				Devanagari.isDevanagari(c) -> {

					flushWord()
					out.append(' ')
				}
				else -> {
					flushWord()
					out.append(c)
				}
			}
			at++
		}
		flushWord()

		return collapseSpaces(out)
	}

	fun pronounceWord(word: String, language: SpokenLanguage): String =
		PronunciationRules.pronounce(word, language)

	fun guessLanguage(text: String): SpokenLanguage = PronunciationRules.guessLanguage(text)

	private fun collapseSpaces(text: StringBuilder): String {
		var spaced = false
		var at = 0
		while (at < text.length) {
			if (text[at] == ' ') {
				if (spaced) {
					text.deleteCharAt(at)
					continue
				}
				spaced = true
			} else {
				spaced = false
			}
			at++
		}
		return text.toString()
	}
}
