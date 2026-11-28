package com.blindtechnexus.freeeloquencetts.pronunciation

internal object SchwaDeletion {

	fun apply(syllables: MutableList<Akshara>, language: SpokenLanguage) {
		if (language == SpokenLanguage.NEPALI) return
		if (syllables.size < 2) return
		val lastIndex = syllables.size - 1
		val firstVowel = syllables.indexOfFirst { it.vowel != null }
		if (firstVowel < 0) return
		val last = syllables[lastIndex]
		if (last.hasSchwa && !last.writtenVowel && firstVowel != lastIndex) {
			last.vowel = null
		}
		for (at in 1 until lastIndex) {
			val syllable = syllables[at]
			if (!syllable.hasSchwa || syllable.writtenVowel) continue
			val previous = syllables[at - 1]
			if (previous.vowel == null) continue
			val next = syllables[at + 1]
			val nextVowel = next.vowel ?: continue
			if (!singleOnset(next.onsetText())) continue
			if (nextVowel.schwa && !next.writtenVowel && at + 1 == lastIndex) continue
			syllable.vowel = null
		}
	}

	private fun singleOnset(onset: String): Boolean = when (onset) {
		"kh", "gh", "Ch", "Jh", "th", "dh", "ph", "bh", "ny", "Fh" -> true
		else -> onset.length == 1
	}

	fun keepsFinalSchwa(language: SpokenLanguage, word: String): Boolean =
		language == SpokenLanguage.NEPALI || word in NEPALI_FINAL_SCHWA

	private val NEPALI_FINAL_SCHWA: Set<String> = setOf(
		"गर्नु", "भन्नु", "हुनु", "जानु", "आउनु", "खानु", "पिनु", "सुन्नु",
		"पढ्नु", "लेख्नु", "दिनु", "लिनु", "बस्नु", "उठ्नु", "सुत्नु", "रुनु",
		"हस्नु", "गाउनु", "नाच्नु", "किन्नु", "बेच्नु", "गर्न", "भन्न", "हुन"
	)
}
