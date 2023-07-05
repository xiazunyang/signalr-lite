package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class InvocationConverter(
    private val target: String,
    private val parameters: List<Any>
) : SignalREventConverter<SignalREvent.Invocation> {

    override fun convert(): SignalREvent.Invocation {
        return SignalREvent.Invocation(target, parameters)
    }

    object Factory : SignalREventConverter.Factory<InvocationConverter> {
        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): InvocationConverter? {
            val type = jsonObject.get("type")?.asInt
            val target = jsonObject.get("target")?.asString ?: return null
            val parameterTypes = signalRHub.argumentTypes[target]
            if (type == 1 && parameterTypes != null) {
                val argumentsJsonArray = jsonObject.getAsJsonArray("arguments")
                val parameters = parameterTypes.mapIndexed { index, clazz ->
                    val jsonElement = argumentsJsonArray[index]
                    signalRHub.gson.fromJson(jsonElement, clazz)
                }
                return InvocationConverter(target, parameters)
            }
            return null
        }
    }

}