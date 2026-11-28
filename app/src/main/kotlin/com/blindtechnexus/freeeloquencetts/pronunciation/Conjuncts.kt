package com.blindtechnexus.freeeloquencetts.pronunciation

internal object Conjuncts {

	private val SPECIAL: Map<String, String> = mapOf(
		"Jny" to "gy",
		"Jn" to "gn",
		"jny" to "gy",
		"jn" to "gn",
		"gy" to "gy",
		"kS" to "kS",
		"ks" to "ks",
		"Sr" to "Sr",
		"tr" to "tr",
		"dr" to "dr",
		"dy" to "dy",
		"dhy" to "dhy",
		"dv" to "dv",
		"st" to "st",
		"sth" to "sth",
		"sn" to "sn",
		"sm" to "sm",
		"sl" to "sl",
		"mb" to "mb",
		"mp" to "mp",
		"nd" to "nd",
		"nt" to "nt",
		"nth" to "nth",
		"nn" to "nn",
		"mm" to "mm",
		"ll" to "ll",
		"tt" to "tt",
		"dd" to "dd",
		"pp" to "pp",
		"bb" to "bb",
		"kk" to "kk",
		"gg" to "gg",
		"CC" to "CC",
		"JJ" to "JJ",
		"kt" to "kt",
		"kht" to "kht",
		"gd" to "gd",
		"gn" to "gn",
		"tm" to "tm",
		"tn" to "tn",
		"pt" to "pt",
		"bd" to "bd",
		"bdh" to "bdh",
		"rk" to "rk",
		"rg" to "rg",
		"rd" to "rd",
		"rn" to "rn",
		"rm" to "rm",
		"rl" to "rl",
		"rv" to "rv",
		"rs" to "rs",
		"rh" to "rh",
		"lk" to "lk",
		"lg" to "lg",
		"lp" to "lp",
		"lm" to "lm",
		"lv" to "lv",
		"vy" to "vy",
		"vr" to "vr",
		"hy" to "hy",
		"hr" to "hr",
		"hm" to "hm",
		"hl" to "hl"
	)

	fun onset(text: String): String {
		if (text.isEmpty()) return text
		SPECIAL[text]?.let { return it }
		return text
	}

	fun tidy(text: String): String {
		if (text.isEmpty()) return text
		var out = text
		while (out.contains("xxx")) out = out.replace("xxx", "xx")
		while (out.contains("nnn")) out = out.replace("nnn", "nn")
		return out
	}
}
