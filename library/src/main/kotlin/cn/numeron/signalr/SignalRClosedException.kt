package cn.numeron.signalr

class SignalRClosedException @JvmOverloads constructor(
    override val message: String?,
    val allowReconnect: Boolean,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        private const val serialVersionUID: Long = -4583689249203809822L
    }

}