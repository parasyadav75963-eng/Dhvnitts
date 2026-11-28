package com.itfb.dhwanitts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import com.blindtechnexus.freeeloquencetts.Eci
import com.blindtechnexus.freeeloquencetts.EvvEngine
import com.blindtechnexus.freeeloquencetts.pronunciation.DevanagariPronunciation
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.tanh

object EloquenceBridge {
    const val MODE_ELOQUENCE = "Built-in ITFB Eloquence"
    const val KEY_PREFIX = "evv__"

    private val lock = Any()
    @Volatile private var activeEngine: EvvEngine? = null
    @Volatile private var activeLangCode: Int = -1
    @Volatile private var activePreset: Int = -1

    @JvmStatic
    fun isEloquenceKey(key: String?): Boolean =
        !key.isNullOrEmpty() && key.startsWith(KEY_PREFIX)

    @JvmStatic
    fun getBuiltInVoices(): ArrayList<VoiceModel> {
        val list = ArrayList<VoiceModel>()
        val presets = Eci.PRESET_NAMES

        // 1. Hindi (Devanagari via built-in DevanagariPronunciation + US English phonetics)
        for (i in presets.indices) {
            list.add(
                VoiceModel(
                    "${KEY_PREFIX}hi_IN__$i",
                    "Hindi",
                    "India",
                    "${presets[i]} [ITFB Eloquence]",
                    "built-in",
                    "builtin://evv/hi_IN/$i",
                    "builtin://evv/hi_IN/$i",
                    "Built-in",
                    true,
                    MODE_ELOQUENCE
                )
            )
        }

        // 2. All 8 built-in OpenEVV languages (US/UK English, German, Spanish, French, Italian)
        for (lang in Eci.LANGUAGES) {
            val langName = when (lang.iso2Language) {
                "en" -> "English"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "it" -> "Italian"
                else -> lang.label
            }
            val country = when (lang.iso2Country) {
                "US" -> "United States"
                "GB" -> "United Kingdom"
                "ES" -> "Spain"
                "MX" -> "Mexico"
                "FR" -> "France"
                "CA" -> "Canada"
                "DE" -> "Germany"
                "IT" -> "Italy"
                else -> lang.iso2Country
            }
            val tag = "${lang.iso2Language}_${lang.iso2Country}"
            for (i in presets.indices) {
                list.add(
                    VoiceModel(
                        "${KEY_PREFIX}${tag}__$i",
                        langName,
                        country,
                        "${presets[i]} ($country) [ITFB Eloquence]",
                        "built-in",
                        "builtin://evv/$tag/$i",
                        "builtin://evv/$tag/$i",
                        "Built-in",
                        true,
                        MODE_ELOQUENCE
                    )
                )
            }
        }
        return list
    }

    private fun parseLangCode(key: String): Int {
        val parts = key.removePrefix(KEY_PREFIX).split("__")
        val tag = parts.getOrNull(0)?.lowercase() ?: "en_us"
        return when {
            tag.startsWith("en_gb") -> Eci.ENGLISH_UK
            tag.startsWith("es_es") -> Eci.SPANISH_SPAIN
            tag.startsWith("es_mx") -> Eci.SPANISH_MEXICO
            tag.startsWith("fr_fr") -> Eci.FRENCH_FRANCE
            tag.startsWith("fr_ca") -> Eci.FRENCH_CANADA
            tag.startsWith("de_") -> Eci.GERMAN
            tag.startsWith("it_") -> Eci.ITALIAN
            else -> Eci.ENGLISH_US
        }
    }

    private fun parsePresetIndex(key: String): Int {
        val parts = key.removePrefix(KEY_PREFIX).split("__")
        val idx = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return idx.coerceIn(0, Eci.PRESET_NAMES.size - 1)
    }

    private fun ensureEngineLocked(langCode: Int, presetIdx: Int): EvvEngine? {
        if (activeEngine != null && activeLangCode != langCode) {
            try { activeEngine?.close() } catch (_: Throwable) {}
            activeEngine = null
            activeLangCode = -1
            activePreset = -1
        }
        if (activeEngine == null) {
            val eng = EvvEngine.open(langCode) ?: return null
            eng.setSampleRate(22050)
            eng.setUpsampleMethod(Eci.UPSAMPLE_LINEAR)
            activeEngine = eng
            activeLangCode = langCode
            activePreset = -1
        }
        val eng = activeEngine ?: return null
        if (activePreset != presetIdx) {
            eng.applyVoice(presetIdx)
            eng.setVolume(90) // Safe headroom to prevent distortion/clipping
            activePreset = presetIdx
        }
        return eng
    }

    private fun prepareText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return if (DevanagariPronunciation.hasDevanagari(trimmed)) {
            DevanagariPronunciation.apply(trimmed, null)
        } else {
            trimmed
        }
    }

    // Soft-limiter to keep audio loud, crisp and 100% distortion-free
    private fun cleanPcmInPlace(buf: ByteArray, length: Int) {
        var i = 0
        while (i + 1 < length) {
            val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            var v = sample * 1.15f
            if (v > 27000f) v = 27000f + (tanh(((v - 27000f) / 8000f).toDouble()).toFloat() * 5200f)
            else if (v < -27000f) v = -27000f - (tanh(((-v - 27000f) / 8000f).toDouble()).toFloat() * 5200f)
            val clamped = v.roundToInt().coerceIn(-32760, 32760)
            buf[i] = (clamped and 0xFF).toByte()
            buf[i + 1] = ((clamped shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    @JvmStatic
    fun stop() {
        try { activeEngine?.stop() } catch (_: Throwable) {}
    }

    @JvmStatic
    fun release() {
        synchronized(lock) {
            try { activeEngine?.close() } catch (_: Throwable) {}
            activeEngine = null
            activeLangCode = -1
            activePreset = -1
        }
    }

    @JvmStatic
    fun streamToCallback(
        voiceKey: String,
        text: String,
        ratePercent: Int,
        pitchPercent: Int,
        cb: SynthesisCallback,
        isStopped: () -> Boolean
    ): Boolean {
        val processed = prepareText(text)
        if (processed.isEmpty()) return false
        synchronized(lock) {
            val eng = ensureEngineLocked(parseLangCode(voiceKey), parsePresetIndex(voiceKey)) ?: return false
            eng.setRatePercent(ratePercent.coerceIn(20, 400))
            eng.setPitchPercent(pitchPercent.coerceIn(25, 200))
            eng.setVolume(90)

            val sr = eng.sampleRateHz
            cb.start(sr, AudioFormat.ENCODING_PCM_16BIT, 1)
            if (!eng.speak(processed, isStopped)) return false

            val maxB = cb.maxBufferSize.let { if (it > 0) it.coerceAtMost(4096) else 2048 }
            val buf = ByteArray(maxB)
            var wroteAny = false
            while (!isStopped()) {
                val n = eng.read(buf, EvvEngine.READ_TIMEOUT_MS)
                if (n <= 0) break
                cleanPcmInPlace(buf, n)
                cb.audioAvailable(buf, 0, n)
                wroteAny = true
            }
            if (isStopped()) eng.stop()
            return wroteAny
        }
    }

    @JvmStatic
    fun synthesizePcm(
        voiceKey: String,
        text: String,
        ratePercent: Int,
        pitchPercent: Int
    ): ByteArray {
        val processed = prepareText(text)
        if (processed.isEmpty()) return ByteArray(0)
        synchronized(lock) {
            val eng = ensureEngineLocked(parseLangCode(voiceKey), parsePresetIndex(voiceKey)) ?: return ByteArray(0)
            eng.setRatePercent(ratePercent.coerceIn(20, 400))
            eng.setPitchPercent(pitchPercent.coerceIn(25, 200))
            eng.setVolume(90)
            if (!eng.speak(processed) { false }) return ByteArray(0)

            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = eng.read(buf, EvvEngine.READ_TIMEOUT_MS)
                if (n <= 0) break
                cleanPcmInPlace(buf, n)
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
