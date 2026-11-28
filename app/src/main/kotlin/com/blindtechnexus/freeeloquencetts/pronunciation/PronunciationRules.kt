package com.blindtechnexus.freeeloquencetts.pronunciation

internal object PronunciationRules {

	fun pronounce(word: String, language: SpokenLanguage): String =
		pronounce(word, language, allowSuffixes = true)

	private fun pronounce(word: String, language: SpokenLanguage, allowSuffixes: Boolean): String {
		if (word.isEmpty()) return word
		if (word.length == 1) return annotate(rules(word, language))
		if (allowSuffixes) suffixSplit(word, language)?.let { return it }
		val phonemes = rules(word, language)
		if (phonemes.isNotEmpty()) return annotate(phonemes)
		return lexicon(word, language) ?: ""
	}

	private fun annotate(phonemes: String): String =
		if (phonemes.isEmpty()) "" else "`[" + phonemes + "]"

	private fun lexicon(word: String, language: SpokenLanguage): String? = when (language) {
		SpokenLanguage.NEPALI -> NepaliLexicon.lookup(word) ?: HindiLexicon.lookup(word)
		else -> HindiLexicon.lookup(word) ?: NepaliLexicon.lookup(word)
	}

	private fun suffixSplit(word: String, language: SpokenLanguage): String? {
		val suffixes = if (language == SpokenLanguage.NEPALI) NEPALI_SUFFIXES else HINDI_SUFFIXES
		for ((suffix, roman) in suffixes) {
			if (word.length < suffix.length + 2 || !word.endsWith(suffix)) continue
			val stem = word.substring(0, word.length - suffix.length)
			if (!Syllabifier.hasDevanagari(stem)) continue
			val stemPhonemes = rules(stem, language)
			if (stemPhonemes.isEmpty()) {
				lexicon(stem, language)?.let { return it + roman }
				continue
			}
			val suffixPhonemes = rules(suffix, language)
			return annotate(Conjuncts.tidy(stemPhonemes + suffixPhonemes))
		}
		return null
	}

	private fun rules(word: String, language: SpokenLanguage): String {
		val syllables = Syllabifier.syllabify(word, language).toMutableList()
		if (syllables.isEmpty()) return ""
		val opening = syllables.firstOrNull()
		if (
			syllables.size > 1 && opening != null && opening.vowel == null &&
			!opening.writtenVowel && opening.onset.length > 1
		) {
			opening.vowel = Devanagari.SCHWA
		}
		SchwaDeletion.apply(syllables, language)
		if (SchwaDeletion.keepsFinalSchwa(language, word)) {
			syllables.lastOrNull()?.takeIf { it.onset.isNotEmpty() }?.let { last ->
				if (!last.writtenVowel && last.vowel == null) last.vowel = Devanagari.SCHWA
			}
		}
		val out = StringBuilder(word.length + 8)
		for (at in syllables.indices) {
			val syllable = syllables[at]
			out.append(Conjuncts.onset(syllable.onsetText()))
			syllable.vowel?.let { out.append(Devanagari.vowelPhoneme(it, language)) }
			out.append(Nasalization.coda(syllable, syllables.getOrNull(at + 1)))
		}
		return Conjuncts.tidy(out.toString())
	}

	private val HINDI_SUFFIXES: List<Pair<String, String>> = listOf(
		"हरुओंमें" to "huroonmehn", "हरुओंको" to "huroonkoh", "हरुओंने" to "huroonneh",
		"हरुओंसे" to "huroonseh", "हरुओं" to "huroon", "हरों" to "huron",
		"हरें" to "hurein", "हरू" to "huroo",
		"वालों" to "vaalon", "वाला" to "vaalaa", "वाले" to "vaaleh", "वाली" to "vaalee",
		"करने" to "kurneh", "करना" to "kurnaa", "करता" to "kurtaa",
		"करती" to "kurtee", "करते" to "kurteh", "किया" to "kiyaa",
		"में" to "mehn", "से" to "seh", "को" to "koh",
		"का" to "kaa", "की" to "kee", "के" to "keh", "पर" to "pur",
		"तक" to "tuk", "ने" to "neh", "ओं" to "on", "ें" to "ain",
		"ों" to "on", "एं" to "ain", "यों" to "yon",
		"ता" to "taa", "ती" to "tee", "ते" to "teh", "ना" to "naa",
		"नी" to "nee", "लिए" to "liyeh", "साथ" to "saath", "बिना" to "binaa"
	)

	private val NEPALI_SUFFIXES: List<Pair<String, String>> = listOf(
		"हरूलाई" to "huroolaaee", "हरूसँग" to "huroosung",
		"हरूबाट" to "huroobaat", "हरूदेखि" to "huroodehkhi",
		"हरूमा" to "huroomaa", "हरूको" to "hurookoh",
		"हरूले" to "hurooleh", "हरू" to "huroo",
		"लाई" to "laaee", "सँग" to "sung", "देखि" to "dehkhi", "बाट" to "baat",
		"सम्म" to "summ", "भित्र" to "bhitru", "बाहिर" to "baahiru",
		"मा" to "maa", "को" to "koh", "का" to "kaa",
		"की" to "kee", "ले" to "leh", "नै" to "nai", "पनि" to "punni",
		"गर्ने" to "gurneh", "गरेको" to "gurehkoh", "भएको" to "bhuhyekoh"
	)

	fun guessLanguage(text: String): SpokenLanguage {
		var hindi = 0
		var nepali = 0
		for (raw in text.split(' ', '\t', '\n', '\u0964', '\u0965', ',', ';', ':', '?', '!', '"', '\u201C', '\u201D')) {
			val word = raw.trim()
			if (word.isEmpty()) continue
			if (word in NEPALI_MARKERS) nepali++
			if (word in HINDI_MARKERS) hindi++
			if (word.length > 3) {
				if (word.endsWith("्छ") || word.endsWith("छन्") || word.endsWith("एको") ||
					word.endsWith("हरू") || word.endsWith("लाई") || word.endsWith("सँग")
				) nepali++
				if (word.endsWith("ता") || word.endsWith("ती") || word.endsWith("ते") ||
					word.endsWith("ओं") || word.endsWith("ें") || word.endsWith("ों")
				) hindi++
			}
		}
		return if (nepali > hindi) SpokenLanguage.NEPALI else SpokenLanguage.HINDI
	}

	val NEPALI_MARKERS: Set<String> = setOf(
		"छ", "छन्", "छु", "छौ", "छैन", "छैनन्", "छिन्", "थिइन्",
		"हुन्छ", "हुन्छन्", "हुन्छु", "हुन्छौ", "हुनुहुन्छ", "हुने", "हुन", "हुन्",
		"होइन", "होस्", "होला", "होलान्", "थियो", "थिए", "थिइन्",
		"गर्छ", "गर्छन्", "गर्छु", "गर्ने", "गर्नु", "गर्न", "गरे", "गर्यो",
		"भयो", "भए", "भएको", "भन्छ", "भन्ने", "भन्नु", "भन्यो",
		"आउँछ", "आउने", "आयो", "जान्छ", "जाने", "गयो",
		"दिन्छ", "दिने", "दियो", "पाउँछ", "पाउने", "पायो",
		"सक्छ", "सक्ने", "पर्छ", "पर्ने", "रहन्छ", "रहेको",
		"गरेको", "दिएको", "आएको", "गएको", "दिइएको", "भनिएको",
		"लाई", "बाट", "देखि", "सँग", "सम्म", "भित्र", "बाहिर",
		"पनि", "अनि", "किनभने", "तर", "वा", "कि", "हो",
		"तपाईं", "तपाई", "हामी", "उनी", "उहाँ", "उन", "यिनी",
		"यो", "त्यो", "ती", "यी", "कसरी", "कहिले", "कहाँ", "किन",
		"धेरै", "थोरै", "सबै", "केही", "अहिले", "आज", "भोलि", "हिजो",
		"नमस्ते", "नमस्कार", "धन्यवाद", "गुनासो", "जानकारी",
		"नेपाल", "काठमाडौं", "पोखरा", "ललितपुर", "भक्तपुर", "बुटवल",
		"रुपैयाँ", "रुपैया", "मोबाइल", "इमेल", "सन्देश", "खबर",
		"सरकार", "मन्त्री", "प्रधानमन्त्री", "अदालत", "विद्यालय", "पुस्तक"
	)

	val HINDI_MARKERS: Set<String> = setOf(
		"है", "हैं", "हूँ", "हूं", "हो", "होता", "होती", "होते",
		"था", "थी", "थे", "थीं", "होगा", "होगी", "होंगे", "हुआ", "हुई", "हुए",
		"करना", "करता", "करती", "करते", "किया", "करूँगा", "करेंगे",
		"जाना", "जाता", "जाती", "जाते", "गया", "गई", "गए", "जाऊँगा",
		"आना", "आता", "आती", "आते", "आया", "आई", "आए",
		"देना", "दिया", "दी", "दिए", "लेना", "लिया", "ली", "लिए",
		"चाहिए", "चाहता", "चाहती", "चाहते", "सकता", "सकती", "सकते",
		"रहा", "रही", "रहे", "रहता", "रहती", "रहते",
		"का", "की", "के", "को", "से", "में", "पर", "तक", "लिए", "साथ",
		"बिना", "और", "या", "लेकिन", "मगर", "क्योंकि", "अगर", "तो",
		"भी", "ही", "नहीं", "न", "सब", "कुछ", "कोई", "किसी",
		"बहुत", "थोड़ा", "थोड़ी", "ज़्यादा", "ज्यादा", "अब", "फिर",
		"आज", "कल", "अभी", "पहले", "बाद", "यहाँ", "वहाँ", "कहाँ", "कब",
		"वह", "वो", "यह", "ये", "मैं", "मुझे", "मेरा", "मेरी", "मेरे",
		"हम", "हमें", "हमारा", "तुम", "तुम्हें", "आप", "आपको", "आपका",
		"क्या", "क्यों", "कैसे", "कौन", "कितना", "कितनी", "कितने",
		"अच्छा", "अच्छी", "अच्छे", "ठीक", "बिल्कुल", "ज़रूर", "शुक्रिया",
		"धन्यवाद", "नमस्ते", "नमस्कार", "कृपया", "माफ़", "क्षमा",
		"भारत", "दिल्ली", "मुंबई", "कोलकाता", "चेन्नई", "बेंगलुरु",
		"रुपया", "रुपये", "मोबाइल", "ईमेल", "संदेश", "समाचार",
		"सरकार", "मंत्री", "प्रधानमंत्री", "अदालत", "विद्यालय", "पुस्तक"
	)
}
