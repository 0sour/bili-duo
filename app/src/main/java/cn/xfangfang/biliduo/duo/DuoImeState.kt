package cn.xfangfang.biliduo.duo

/**
 * Tracks IME visibility requested from Compose. Window insets on the bottom
 * Presentation do not always report the hide, so the session uses this flag to
 * avoid swallowing every subsequent B.
 */
object DuoImeState {
    var shown: Boolean = false
    var generation: Int = 0
        private set

    fun onShown() {
        shown = true
        generation += 1
    }

    fun onHiddenByBack() {
        shown = false
        generation += 1
    }
}
