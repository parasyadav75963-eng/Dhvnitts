package com.blindtechnexus.freeeloquencetts

internal object EvvNative {

	@Volatile
	var loadError: Throwable? = null
		private set

	val loaded: Boolean

	init {
		var ok = false
		try {
			System.loadLibrary("evvjni")
			ok = true
		} catch (e: UnsatisfiedLinkError) {
			loadError = e
		} catch (e: Throwable) {

			loadError = e
		}
		loaded = ok
	}

	external fun create(language: Int): Long

	external fun destroy(handle: Long)

	external fun speak(handle: Long, text: ByteArray): Boolean

	external fun read(handle: Long, dst: ByteArray, timeoutMs: Int): Int

	external fun stop(handle: Long)

	external fun setParam(handle: Long, param: Int, value: Int): Int

	external fun getParam(handle: Long, param: Int): Int

	external fun setVoiceParam(handle: Long, voice: Int, param: Int, value: Int): Int

	external fun getVoiceParam(handle: Long, voice: Int, param: Int): Int

	external fun copyVoice(handle: Long, from: Int, to: Int): Int

	external fun loadDictionary(handle: Long, volume: Int, path: String): Int

	external fun teachWord(handle: Long, volume: Int, key: ByteArray, say: ByteArray): Int

	external fun lookUpWord(handle: Long, volume: Int, key: ByteArray): String?

	external fun forgetDictionaries(handle: Long)

	external fun version(): String

	external fun availableLanguages(): IntArray

	external fun setUpsampleMethod(method: Int)
}
