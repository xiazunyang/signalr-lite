package cn.numeron.signalr

class SignalRInvocationException @JvmOverloads constructor(
    override val message: String?,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        private const val serialVersionUID: Long = -4583689249203809821L
    }

}