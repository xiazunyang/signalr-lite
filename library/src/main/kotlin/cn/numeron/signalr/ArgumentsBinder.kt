package cn.numeron.signalr

interface ArgumentsBinder {

    fun getReturnType(invocationId: String): Class<*>

    fun getParameterTypes(target: String): List<Class<*>>

}