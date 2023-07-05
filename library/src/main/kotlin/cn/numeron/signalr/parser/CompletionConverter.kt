package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class CompletionConverter(
    private val invocationId: String,
    private val result: Any?,
    private val error: String?,
) : SignalREventConverter<SignalREvent.Completion> {

    override fun convert(): SignalREvent.Completion {
        return SignalREvent.Completion(invocationId, result, error)
    }

    object Factory : SignalREventConverter.Factory<CompletionConverter> {
        override fun getConverter(
            jsonObject: JsonObject,
            signalRHub: SignalRHub
        ): CompletionConverter? {
            val type = jsonObject.get("type")?.asInt
            if (type == 3) {
                val invocationId = jsonObject.get("invocationId")?.asString ?: return null
                val returnType = signalRHub.returnTypes[invocationId]
                val returns = when (returnType?.simpleName) {
                    "void" -> null
                    "Void", "Unit" -> {
                        val constructor = returnType.getDeclaredConstructor()
                        constructor.isAccessible = true
                        constructor.newInstance()
                    }
                    else -> signalRHub.gson.fromJson(jsonObject.get("result"), returnType)
                }
                val error = jsonObject.get("error")?.asString
                return CompletionConverter(invocationId, returns, error)
            }
            return null
        }
    }

}