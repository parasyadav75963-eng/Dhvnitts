package com.blindtechnexus.freeeloquencetts

object Eci {

	const val PARAM_SYNTH_MODE = 0
	const val PARAM_INPUT_TYPE = 1
	const val PARAM_TEXT_MODE = 2
	const val PARAM_DICTIONARY = 3
	const val PARAM_SAMPLE_RATE = 5
	const val PARAM_REAL_WORLD_UNITS = 8
	const val PARAM_LANGUAGE_DIALECT = 9
	const val PARAM_NUMBER_MODE = 10

	const val VOICE_GENDER = 0
	const val VOICE_HEAD_SIZE = 1
	const val VOICE_PITCH_BASELINE = 2
	const val VOICE_PITCH_FLUCTUATION = 3
	const val VOICE_ROUGHNESS = 4
	const val VOICE_BREATHINESS = 5
	const val VOICE_SPEED = 6
	const val VOICE_VOLUME = 7

	const val VOICE_CURRENT = 0
	const val FIRST_PRESET = 1
	const val LAST_PRESET = 8
	const val SCRATCH_VOICE = 9

	const val NUM_VOICE_PARAMS = 8

	const val DICT_MAIN = 0
	const val DICT_ROOT = 1
	const val DICT_ABBREVIATION = 2

	val DICT_VOLUMES = listOf(DICT_MAIN, DICT_ROOT, DICT_ABBREVIATION)

	const val DEFAULT_SPEED = 50

	const val SPEED_MAX = 250
	const val PERCENT_MAX = 100

	fun voiceRange(param: Int): IntRange = when (param) {
		VOICE_GENDER -> 0..1
		VOICE_SPEED -> 0..SPEED_MAX
		else -> 0..PERCENT_MAX
	}

	fun clampVoice(param: Int, value: Int): Int {
		val range = voiceRange(param)
		return value.coerceIn(range.first, range.last)
	}

	fun toPercent(param: Int, value: Int): Int {
		val top = voiceRange(param).last
		if (top <= 0) return 0
		return Math.round(value * 100.0 / top).toInt().coerceIn(0, PERCENT_MAX)
	}

	fun fromPercent(param: Int, percent: Int): Int {
		val top = voiceRange(param).last
		return Math.round(percent.coerceIn(0, PERCENT_MAX) * top / 100.0).toInt()
	}

	val SAMPLE_RATES = intArrayOf(8000, 11025, 22050, 16000, 32000, 44100, 48000)

	fun sampleRateHz(index: Int): Int =
		SAMPLE_RATES.getOrElse(index) { SAMPLE_RATES[1] }

	fun sampleRateIndex(hz: Int): Int {
		val at = SAMPLE_RATES.indexOf(nearestSampleRate(hz))
		return if (at >= 0) at else 1
	}

	val PRESET_NAMES = arrayOf(
		"Reed", "Shelley", "Bobby", "Rocko", "Glen", "Sandy", "Grandma", "Grandpa", "Luna"
	)

	const val LUNA_INDEX = 8

	const val LUNA_BASE_PRESET = 6

	val LUNA_SHAPE: Map<Int, Int> = mapOf(
		VOICE_GENDER to 1,
		VOICE_PITCH_BASELINE to 92,
		VOICE_PITCH_FLUCTUATION to 70,
		VOICE_HEAD_SIZE to 12,
		VOICE_ROUGHNESS to 3,
		VOICE_BREATHINESS to 38,
		VOICE_VOLUME to 92
	)

	fun defaultGender(index: Int): Int = if (index == LUNA_INDEX) 1 else 0

	const val ENGLISH_US = 0x00010000
	const val ENGLISH_UK = 0x00010001
	const val SPANISH_SPAIN = 0x00020000
	const val SPANISH_MEXICO = 0x00020001
	const val FRENCH_FRANCE = 0x00030000
	const val FRENCH_CANADA = 0x00030001
	const val GERMAN = 0x00040000
	const val ITALIAN = 0x00050000

	class Language(
		val code: Int,
		val iso2Language: String,
		val iso3Language: String,
		val iso2Country: String,
		val iso3Country: String,
		val label: String,
		val module: String
	) {

		val tag: String get() = "$iso2Language-$iso2Country"

		fun locale(): java.util.Locale = java.util.Locale(iso2Language, iso2Country)

		fun speaks(lang: String?): Boolean =
			lang != null && (lang.equals(iso2Language, true) || lang.equals(iso3Language, true))

		fun inCountry(country: String?): Boolean =
			country != null && (country.equals(iso2Country, true) || country.equals(iso3Country, true))
	}

	val LANGUAGES = listOf(
		Language(ENGLISH_US, "en", "eng", "US", "USA", "English (United States)", "enus"),
		Language(ENGLISH_UK, "en", "eng", "GB", "GBR", "English (United Kingdom)", "engb"),
		Language(SPANISH_SPAIN, "es", "spa", "ES", "ESP", "Spanish (Spain)", "eses"),
		Language(SPANISH_MEXICO, "es", "spa", "MX", "MEX", "Spanish (Mexico)", "esus"),
		Language(FRENCH_FRANCE, "fr", "fra", "FR", "FRA", "French (France)", "frfr"),
		Language(FRENCH_CANADA, "fr", "fra", "CA", "CAN", "French (Canada)", "frca"),
		Language(GERMAN, "de", "deu", "DE", "DEU", "German", "dede"),
		Language(ITALIAN, "it", "ita", "IT", "ITA", "Italian", "itit")
	)

	const val ISO3_LANGUAGE = "eng"

	const val ISO2_LANGUAGE = "en"

	fun countryOf(language: Int): String = languageOf(language).iso3Country

	fun iso2CountryOf(language: Int): String = languageOf(language).iso2Country

	fun languageOf(code: Int): Language =
		LANGUAGES.firstOrNull { it.code == code } ?: LANGUAGES[0]

	fun languageOrDefault(code: Int): Int =
		if (LANGUAGES.any { it.code == code }) code else ENGLISH_US

	fun knowsLanguage(code: Int): Boolean = LANGUAGES.any { it.code == code }

	fun languageFor(lang: String?, country: String?): Language? {
		if (lang.isNullOrEmpty()) return null
		var family: Language? = null
		for (language in LANGUAGES) {
			if (!language.speaks(lang)) continue
			if (language.inCountry(country)) return language
			if (family == null) family = language
		}
		return family
	}

	const val ISO3_COUNTRY = "USA"

	fun voiceName(preset: Int, language: Int = ENGLISH_US): String {
		val spoken = languageOf(language)
		return spoken.iso3Language + "-" + spoken.iso3Country + "-" + PRESET_NAMES[preset]
	}

	fun languageOfVoiceName(name: String): Language? {
		val parts = name.split('-', '_')
		if (parts.size < 2) return null
		return languageFor(parts[0], parts[1])
	}

	fun nearestSampleRate(hz: Int): Int {
		if (hz <= 0) return SAMPLE_RATES[1]
		var best = SAMPLE_RATES[1]
		var distance = Int.MAX_VALUE
		for (rate in SAMPLE_RATES) {
			val away = kotlin.math.abs(rate - hz)
			if (away < distance) {
				distance = away
				best = rate
			}
		}
		return best
	}

	const val UPSAMPLE_HOLD = 0

	const val UPSAMPLE_ZEROS = 1
	const val UPSAMPLE_LINEAR = 2
	const val UPSAMPLE_CUBIC = 3
	const val UPSAMPLE_SINC = 4
}
