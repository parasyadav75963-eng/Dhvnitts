package com.blindtechnexus.freeeloquencetts.pronunciation

internal object Nasalization {

	fun coda(syllable: Akshara, next: Akshara?): String {
		val nasal = syllable.nasal
		if (nasal == '\u0000') {
			return if (syllable.visarga) "h" else ""
		}
		if (nasal == Devanagari.CHANDRABINDU) {
			return place(next)
		}
		return place(next)
	}

	private fun place(next: Akshara?): String {
		val onset = next?.onsetText().orEmpty()
		if (onset.isEmpty()) return "n"
		return when {
			onset.startsWith("ph") || onset.startsWith("bh") || onset.startsWith("p") ||
				onset.startsWith("b") || onset.startsWith("m") || onset.startsWith("f") ||
				onset.startsWith("v") || onset.startsWith("w") -> "m"
			onset.startsWith("kh") || onset.startsWith("gh") || onset.startsWith("k") ||
				onset.startsWith("g") || onset.startsWith("G") -> "G"
			onset.startsWith("Ch") || onset.startsWith("C") || onset.startsWith("Jh") ||
				onset.startsWith("J") || onset.startsWith("ny") || onset.startsWith("y") ||
				onset.startsWith("S") -> "ny"
			else -> "n"
		}
	}
}
