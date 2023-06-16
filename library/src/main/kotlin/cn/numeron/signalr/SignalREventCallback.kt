package cn.numeron.signalr

interface SignalREventCallback<out T> {

    fun onSuccess(value: @UnsafeVariance T?)

    fun onFailure(error: String?)

}