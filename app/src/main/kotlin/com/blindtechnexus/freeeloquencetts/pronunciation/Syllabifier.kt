package com.blindtechnexus.freeeloquencetts.pronunciation

internal object Syllabifier {

	fun syllabify(word: String, language: SpokenLanguage = SpokenLanguage.HINDI): List<Akshara> {
		val out = ArrayList<Akshara>(word.length)
		var current: Akshara? = null
		var clusterOpen = false

		fun flush() {
			val held = current
			if (held != null && !held.isEmpty) out.add(held)
			current = null
			clusterOpen = false
		}

		fun start(): Akshara {
			val made = Akshara()
			current = made
			return made
		}

		var i = 0
		while (i < word.length) {
			val c = word[i]
			when {
				Devanagari.isConsonant(c) -> {
					val base = c
					var phoneme = Devanagari.consonantPhoneme(base, language)
					if (i + 1 < word.length && word[i + 1] == Devanagari.NUKTA) {
						phoneme = Devanagari.NUKTA_FORMS[base] ?: phoneme
						i++
					}
					if (!clusterOpen || current == null) {
						if (current != null) flush()
						start().onset.append(phoneme)
					} else {
						current?.onset?.append(phoneme)
					}
					clusterOpen = false
				}

				c == Devanagari.VIRAMA -> {
					current?.vowel = null
					clusterOpen = true
				}

				Devanagari.isMatra(c) -> {
					val held = current ?: start()
					held.vowel = Devanagari.MATRAS[c]
					held.writtenVowel = true
					clusterOpen = false
				}

				Devanagari.isIndependent(c) -> {
					if (current != null) flush()
					val held = start()
					held.vowel = Devanagari.INDEPENDENT[c]
					held.independent = true
					held.writtenVowel = true
					clusterOpen = false
				}

				c == Devanagari.ANUSVARA || c == Devanagari.CHANDRABINDU -> {
					val held = current
					if (held != null) held.nasal = c
				}

				c == Devanagari.VISARGA -> {
					current?.visarga = true
				}

				c == Devanagari.OM -> {
					if (current != null) flush()
					val held = start()
					held.onset.append("om")
					held.vowel = null
					flush()
				}

				c == Devanagari.AVAGRAHA -> {
					clusterOpen = false
				}

				c == Devanagari.ZERO_WIDTH_JOINER || c == Devanagari.ZERO_WIDTH_NON_JOINER -> {
				}

				c == Devanagari.NUKTA -> {
				}

				Devanagari.isDevanagari(c) || Devanagari.isSilentMark(c) -> {
				}

				else -> {
					flush()
				}
			}
			i++
		}
		flush()
		return out
	}

	fun hasDevanagari(word: String): Boolean {
		for (c in word) if (Devanagari.isLetter(c) && Devanagari.isDevanagari(c)) return true
		return false
	}
}
