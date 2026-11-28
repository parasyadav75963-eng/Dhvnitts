package com.blindtechnexus.freeeloquencetts.pronunciation

internal object Devanagari {

	const val VIRAMA = '\u094D'
	const val ANUSVARA = '\u0902'
	const val CHANDRABINDU = '\u0901'
	const val VISARGA = '\u0903'
	const val NUKTA = '\u093C'
	const val AVAGRAHA = '\u093D'
	const val OM = '\u0950'
	const val DANDA = '\u0964'
	const val DOUBLE_DANDA = '\u0965'
	const val ABBREVIATION_SIGN = '\u0970'
	const val HIGH_SPACING_DOT = '\u0971'
	const val ZERO_WIDTH_NON_JOINER = '\u200C'
	const val ZERO_WIDTH_JOINER = '\u200D'

	class Vowel(val letter: Char, val phoneme: String, val ipa: String, val schwa: Boolean = false)

	val SCHWA = Vowel('\u0905', "x", "\u0259", schwa = true)

	val MATRAS: Map<Char, Vowel> = listOf(
		Vowel('\u093E', "a", "a\u02D0"),
		Vowel('\u093F', "I", "\u026A"),
		Vowel('\u0940', "i", "i\u02D0"),
		Vowel('\u0941', "U", "\u028A"),
		Vowel('\u0942', "u", "u\u02D0"),
		Vowel('\u0943', "rI", "r\u0329"),
		Vowel('\u0944', "rI", "r\u0329\u02D0"),
		Vowel('\u0945', "E", "e"),
		Vowel('\u0946', "e", "e"),
		Vowel('\u0947', "e", "e\u02D0"),
		Vowel('\u0948', "E", "\u025B\u02D0"),
		Vowel('\u0949', "c", "\u0252"),
		Vowel('\u094A', "o", "o\u02D0"),
		Vowel('\u094B', "o", "o\u02D0"),
		Vowel('\u094C', "c", "\u0254\u02D0"),
		Vowel('\u094E', "e", "e"),
		Vowel('\u094F', "c", "\u0254\u02D0")
	).associateBy { it.letter }

	val INDEPENDENT: Map<Char, Vowel> = listOf(
		Vowel('\u0905', "x", "\u0259", schwa = true),
		Vowel('\u0906', "a", "a\u02D0"),
		Vowel('\u0907', "I", "\u026A"),
		Vowel('\u0908', "i", "i\u02D0"),
		Vowel('\u0909', "U", "\u028A"),
		Vowel('\u090A', "u", "u\u02D0"),
		Vowel('\u090B', "rI", "r\u0329"),
		Vowel('\u090C', "lI", "l\u0329"),
		Vowel('\u090D', "E", "e"),
		Vowel('\u090E', "e", "e"),
		Vowel('\u090F', "e", "e\u02D0"),
		Vowel('\u0910', "E", "\u025B\u02D0"),
		Vowel('\u0911', "c", "\u0252"),
		Vowel('\u0912', "o", "o\u02D0"),
		Vowel('\u0913', "o", "o\u02D0"),
		Vowel('\u0914', "c", "\u0254\u02D0")
	).associateBy { it.letter }

	class Consonant(
		val letter: Char,
		val phoneme: String,
		val ipa: String,
		val stop: Boolean = false,
		val nasal: Boolean = false,
		val liquid: Boolean = false,
		val fricative: Boolean = false
	)

	val CONSONANTS: Map<Char, Consonant> = listOf(
		Consonant('\u0915', "k", "k", stop = true),
		Consonant('\u0916', "kh", "k\u02B0", stop = true),
		Consonant('\u0917', "g", "\u0261", stop = true),
		Consonant('\u0918', "gh", "\u0261\u02B1", stop = true),
		Consonant('\u0919', "G", "\u014B", nasal = true),

		Consonant('\u091A', "C", "t\u0361\u0283", stop = true),
		Consonant('\u091B', "Ch", "t\u0361\u0283\u02B0", stop = true),
		Consonant('\u091C', "J", "d\u0361\u0292", stop = true),
		Consonant('\u091D', "Jh", "d\u0361\u0292\u02B1", stop = true),
		Consonant('\u091E', "ny", "\u0272", nasal = true),

		Consonant('\u091F', "t", "\u0288", stop = true),
		Consonant('\u0920', "th", "\u0288\u02B0", stop = true),
		Consonant('\u0921', "d", "\u0256", stop = true),
		Consonant('\u0922', "dh", "\u0256\u02B1", stop = true),
		Consonant('\u0923', "n", "\u0273", nasal = true),

		Consonant('\u0924', "t", "t\u032A", stop = true),
		Consonant('\u0925', "th", "t\u032A\u02B0", stop = true),
		Consonant('\u0926', "d", "d\u032A", stop = true),
		Consonant('\u0927', "dh", "d\u032A\u02B1", stop = true),
		Consonant('\u0928', "n", "n", nasal = true),

		Consonant('\u092A', "p", "p", stop = true),
		Consonant('\u092B', "ph", "p\u02B0", stop = true),
		Consonant('\u092C', "b", "b", stop = true),
		Consonant('\u092D', "bh", "b\u02B1", stop = true),
		Consonant('\u092E', "m", "m", nasal = true),

		Consonant('\u092F', "y", "j", liquid = true),
		Consonant('\u0930', "r", "r", liquid = true),
		Consonant('\u0932', "l", "l", liquid = true),
		Consonant('\u0933', "l", "\u026D", liquid = true),
		Consonant('\u0934', "l", "\u026D", liquid = true),
		Consonant('\u0935', "v", "\u028B", liquid = true),
		Consonant('\u0936', "S", "\u0283", fricative = true),
		Consonant('\u0937', "S", "\u0282", fricative = true),
		Consonant('\u0938', "s", "s", fricative = true),
		Consonant('\u0939', "h", "\u0266", fricative = true)
	).associateBy { it.letter }

	val NUKTA_FORMS: Map<Char, String> = mapOf(
		'\u0915' to "k",
		'\u0916' to "h",
		'\u0917' to "g",
		'\u091C' to "z",
		'\u0921' to "F",
		'\u0922' to "Fh",
		'\u092B' to "f",
		'\u092F' to "y",
		'\u0928' to "n"
	)

	private val NEPALI_VOWELS: Map<Char, String> = mapOf(
		'\u0908' to "i",
		'\u090A' to "U",
		'\u0910' to "xI",
		'\u0914' to "xU",
		'\u0940' to "i",
		'\u0942' to "U",
		'\u0948' to "xI",
		'\u0949' to "an",
		'\u094C' to "xU",
		'\u0945' to "xE"
	)

	private val NEPALI_CONSONANTS: Map<Char, String> = mapOf(
		'\u0935' to "w",
		'\u0937' to "s"
	)

	val DIGITS: Map<Char, Char> = mapOf(
		'\u0966' to '0', '\u0967' to '1', '\u0968' to '2', '\u0969' to '3',
		'\u096A' to '4', '\u096B' to '5', '\u096C' to '6', '\u096D' to '7',
		'\u096E' to '8', '\u096F' to '9'
	)

	private val SILENT_MARKS = setOf(
		'\u0900', '\u0951', '\u0952', '\u0953', '\u0954', '\u0955', '\u0956',
		'\u0957', '\u0962', '\u0963', '\u0970', '\u0971'
	)

	fun isConsonant(c: Char): Boolean = CONSONANTS.containsKey(c)

	fun isMatra(c: Char): Boolean = MATRAS.containsKey(c)

	fun isIndependent(c: Char): Boolean = INDEPENDENT.containsKey(c)

	fun isCombining(c: Char): Boolean =
		c == VIRAMA || c == NUKTA || c == ANUSVARA || c == CHANDRABINDU || c == VISARGA ||
			isMatra(c)

	fun isLetter(c: Char): Boolean =
		isConsonant(c) || isMatra(c) || isIndependent(c) || isCombining(c) || c == AVAGRAHA

	fun isDevanagari(c: Char): Boolean = c.code in 0x0900..0x097F

	fun isSilentMark(c: Char): Boolean = c in SILENT_MARKS

	fun vowelPhoneme(vowel: Vowel, language: SpokenLanguage): String =
		if (language == SpokenLanguage.NEPALI) NEPALI_VOWELS[vowel.letter] ?: vowel.phoneme
		else vowel.phoneme

	fun consonantPhoneme(letter: Char, language: SpokenLanguage): String {
		val base = CONSONANTS[letter]?.phoneme ?: return ""
		if (language != SpokenLanguage.NEPALI) return base
		return NEPALI_CONSONANTS[letter] ?: base
	}
}
