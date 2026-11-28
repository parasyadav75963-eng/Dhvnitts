package com.blindtechnexus.freeeloquencetts

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

class EvvEngine private constructor(@Volatile private var handle: Long, val language: Int) {

	private val guard = Any()

	@Volatile
	private var closed = false

	private val reading = AtomicInteger()

	var sampleRateHz: Int = Eci.sampleRateHz(1)
		private set

	private var upsampleMethod = -1

	private var baseSpeed = -1
	private var basePitch = -1

	private var currentRate = -1
	private var currentPitch = -1

	companion object {
		private const val TAG = "freeeloquencetts"

		const val READ_TIMEOUT_MS = 3000 

		const val CLOSE_WAIT_MS = 500L 

		const val CLOSE_HARD_WAIT_MS = 30000L

		const val CLOSE_POLL_MS = 5L 

		val version: String by lazy {
			if (!EvvNative.loaded) "" else runCatching { EvvNative.version() }.getOrDefault("")
		}

		fun isBlankSpeech(text: String): Boolean {
			var i = 0
			val n = text.length
			while (i < n) {
				val c = text[i]
				if (c == '`') {
					val end = annotationEnd(text, i)
					if (end > i + 2 && text[end - 1] == ']') {
						val open = text.indexOf('[', i + 1)
						if (open in (i + 1) until (end - 1)) {
							for (k in (open + 1) until (end - 1)) {
								if (!text[k].isWhitespace()) return false
							}
						}
					}
					i = end
					continue
				}
				if (!c.isWhitespace()) return false
				i++
			}
			return true
		}

		internal fun annotationEnd(text: CharSequence, at: Int): Int {
			val n = text.length
			var k = at + 1
			if (k >= n || text[k].isWhitespace()) return k
			while (k < n && !text[k].isWhitespace()) {
				if (text[k] == '[') {
					var close = k + 1
					while (close < n && text[close] != ']' && text[close] != '\n' && text[close] != '\r') {
						close++
					}
					if (close < n && text[close] == ']') return close + 1
					break
				}
				k++
			}
			while (k < n && !text[k].isWhitespace()) k++
			return k
		}

		const val MAX_SPEAK_CHARS = 256 * 1024

		fun open(language: Int): EvvEngine? {
			if (!EvvNative.loaded) {
				Log.e(TAG, "the native library did not load", EvvNative.loadError)
				return null
			}
			val handle = try {
				EvvNative.create(language)
			} catch (broken: Throwable) {
				Log.e(TAG, "the native instance would not open", broken)
				return null
			}
			if (handle == 0L) {
				Log.e(TAG, "no engine instance for language 0x%08x".format(language))
				return null
			}
			return try {
				EvvEngine(handle, language).apply { configure() }
			} catch (broken: Throwable) {
				Log.e(TAG, "the engine instance would not configure", broken)
				try {
					EvvNative.destroy(handle)
				} catch (forgotten: Throwable) {

				}
				null
			}
		}
	}

	private fun configure() {

		try { EvvNative.setParam(handle, Eci.PARAM_LANGUAGE_DIALECT, language) } catch (_: Throwable) {}
		try { EvvNative.setParam(handle, Eci.PARAM_SYNTH_MODE, 0) } catch (_: Throwable) {}
		try { EvvNative.setParam(handle, Eci.PARAM_REAL_WORLD_UNITS, 0) } catch (_: Throwable) {}

		try { setAnnotations(true) } catch (_: Throwable) {}
		try {
			setSampleRate(Eci.sampleRateHz(EvvNative.getParam(handle, Eci.PARAM_SAMPLE_RATE)))
		} catch (_: Throwable) {
		}
	}

	fun setSampleRate(hz: Int) = synchronized(guard) {
		if (closed) return
		val index = Eci.sampleRateIndex(hz)

		val took = try {
			EvvNative.setParam(handle, Eci.PARAM_SAMPLE_RATE, index)
		} catch (_: Throwable) {
			-1
		}
		if (took == -1) return

		val now = try {
			EvvNative.getParam(handle, Eci.PARAM_SAMPLE_RATE)
		} catch (_: Throwable) {
			index
		}
		sampleRateHz = Eci.sampleRateHz(if (now in Eci.SAMPLE_RATES.indices) now else index)
	}

	fun setUpsampleMethod(method: Int) = synchronized(guard) {
		if (closed || method == upsampleMethod) return
		try {
			EvvNative.setUpsampleMethod(method)
		} catch (_: Throwable) {
			return
		}

		val took = try {
			EvvNative.setParam(handle, Eci.PARAM_SAMPLE_RATE, Eci.sampleRateIndex(sampleRateHz))
		} catch (_: Throwable) {
			-1
		}
		if (took != -1) {
			upsampleMethod = method
		}
	}

	fun setAnnotations(on: Boolean) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setParam(handle, Eci.PARAM_INPUT_TYPE, if (on) 1 else 0)
			} catch (_: Throwable) {
			}
		}
	}

	fun setAbbreviations(on: Boolean) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setParam(handle, Eci.PARAM_DICTIONARY, if (on) 0 else 1)
			} catch (_: Throwable) {
			}
		}
	}

	fun presetShape(index: Int): Map<Int, Int> = synchronized(guard) {
		if (closed) return emptyMap()
		if (index == Eci.LUNA_INDEX) {
			val base = readPresetLocked(Eci.LUNA_BASE_PRESET)
			if (base.isEmpty()) return Eci.LUNA_SHAPE
			val out = LinkedHashMap<Int, Int>(base)
			out.putAll(Eci.LUNA_SHAPE)
			return out
		}
		val preset = (index + Eci.FIRST_PRESET).coerceIn(Eci.FIRST_PRESET, Eci.LAST_PRESET)
		return readPresetLocked(preset)
	}

	private fun readPresetLocked(preset: Int): Map<Int, Int> {
		val out = LinkedHashMap<Int, Int>()
		for (param in 0 until Eci.NUM_VOICE_PARAMS) {
			val value = try {
				EvvNative.getVoiceParam(handle, preset, param)
			} catch (_: Throwable) {
				-1
			}
			if (value >= 0) out[param] = value
		}
		return out
	}

	fun applyVoice(index: Int, shape: Map<Int, Int> = emptyMap()) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				val preset = if (index == Eci.LUNA_INDEX) Eci.LUNA_BASE_PRESET
				else (index + Eci.FIRST_PRESET).coerceIn(Eci.FIRST_PRESET, Eci.LAST_PRESET)
				EvvNative.copyVoice(handle, preset, Eci.SCRATCH_VOICE)
				if (index == Eci.LUNA_INDEX) {
					for ((param, value) in Eci.LUNA_SHAPE) {
						if (param == Eci.VOICE_SPEED) continue
						EvvNative.setVoiceParam(handle, Eci.SCRATCH_VOICE, param, Eci.clampVoice(param, value))
					}
				}

				EvvNative.setVoiceParam(
					handle, Eci.SCRATCH_VOICE, Eci.VOICE_SPEED,
					shape[Eci.VOICE_SPEED] ?: Eci.DEFAULT_SPEED
				)
				for ((param, value) in shape) {
					EvvNative.setVoiceParam(handle, Eci.SCRATCH_VOICE, param, Eci.clampVoice(param, value))
				}
				EvvNative.copyVoice(handle, Eci.SCRATCH_VOICE, Eci.VOICE_CURRENT)
				baseSpeed = EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_SPEED)
				basePitch = EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_PITCH_BASELINE)
				currentRate = -1
				currentPitch = -1
			} catch (_: Throwable) {

				Log.e(TAG, "the voice would not apply")
			}
		}
	}

	fun setRatePercent(percent: Int) = synchronized(guard) {
		if (closed || percent == currentRate || baseSpeed < 0) return

		val want = SpeechRate.speedForPercent(baseSpeed, percent)
		try {
			EvvNative.setVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_SPEED, want)
		} catch (_: Throwable) {
			return
		}
		currentRate = percent
	}

	fun setPitchPercent(percent: Int) = synchronized(guard) {
		if (closed || percent == currentPitch || basePitch < 0) return
		val want = (basePitch.toLong() * percent / 100).toInt().coerceIn(0, Eci.PERCENT_MAX)
		try {
			EvvNative.setVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_PITCH_BASELINE, want)
		} catch (_: Throwable) {
			return
		}
		currentPitch = percent
	}

	fun setVolume(value: Int) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setVoiceParam(
					handle, Eci.VOICE_CURRENT, Eci.VOICE_VOLUME, Eci.clampVoice(Eci.VOICE_VOLUME, value)
				)
			} catch (_: Throwable) {
			}
		}
	}

	fun getVoiceParam(param: Int): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, param)
		} catch (_: Throwable) {
			-1
		}
	}

	fun loadDictionary(volume: Int, path: String): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.loadDictionary(handle, volume, path)
		} catch (_: Throwable) {
			-1
		}
	}

	fun teachWord(volume: Int, key: String, say: String): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.teachWord(
				handle, volume, WesternText.encode(key), WesternText.encode(say)
			)
		} catch (_: Throwable) {
			-1
		}
	}

	fun lookUpWord(volume: Int, key: String): String? = synchronized(guard) {
		if (closed) return null
		return try {
			EvvNative.lookUpWord(handle, volume, WesternText.encode(key))
		} catch (_: Throwable) {
			null
		}
	}

	fun forgetDictionaries() = synchronized(guard) {
		if (closed) return
		try {
			EvvNative.forgetDictionaries(handle)
		} catch (_: Throwable) {
		}
	}

	fun speak(text: String, isStopped: () -> Boolean = { false }): Boolean = synchronized(guard) {
		if (closed || isStopped()) return false
		return try {
			if (text.isBlank() || text.length > MAX_SPEAK_CHARS) return false
			val bytes = WesternText.encode(text)
			if (bytes.isEmpty()) return false
			EvvNative.speak(handle, bytes)
		} catch (refused: Throwable) {
			Log.e(TAG, "the engine refused the text", refused)
			false
		}
	}

	fun read(into: ByteArray, timeoutMs: Int = READ_TIMEOUT_MS): Int {

		reading.incrementAndGet()
		try {
			if (closed) return -1
			val h = handle
			if (h == 0L) return -1
			return try {
				EvvNative.read(h, into, timeoutMs)
			} catch (broken: Throwable) {
				Log.e(TAG, "the read failed", broken)
				-1
			}
		} finally {
			reading.decrementAndGet()
		}
	}

	fun stop() = synchronized(guard) {
		if (closed) return
		try {
			EvvNative.stop(handle)
		} catch (_: Throwable) {
		}
	}

	fun close() = synchronized(guard) {
		if (closed) return
		closed = true
		try {
			EvvNative.stop(handle)
		} catch (_: Throwable) {
		}
		if (awaitReader()) {
			val h = handle
			handle = 0
			try {
				EvvNative.destroy(h)
			} catch (_: Throwable) {
			}
			return
		}
		val orphan = handle
		handle = 0
		Thread({
			var waited = 0
			while (reading.get() > 0 && waited < CLOSE_HARD_WAIT_MS) {
				try {
					Thread.sleep(CLOSE_POLL_MS)
				} catch (stopping: InterruptedException) {
					Thread.currentThread().interrupt()
					break
				}
				waited += CLOSE_POLL_MS.toInt()
			}
			try {
				EvvNative.destroy(orphan)
			} catch (_: Throwable) {
			}
		}, "evv-close").also { it.isDaemon = true }.start()
	}

	private fun awaitReader(): Boolean {
		var waited = 0
		while (reading.get() > 0 && waited < CLOSE_WAIT_MS) {
			try {
				Thread.sleep(CLOSE_POLL_MS)
			} catch (stopping: InterruptedException) {
				Thread.currentThread().interrupt()
				return reading.get() == 0
			}
			waited += CLOSE_POLL_MS.toInt()
		}
		return reading.get() == 0
	}
}
