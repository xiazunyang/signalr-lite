package cn.numeron.signalr

@Target(AnnotationTarget.FUNCTION)
annotation class SignalRInvocation(val alias: String = "")
