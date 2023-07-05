package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class InvocationBindingFailureConverter(
    private val target: String,
    private val invocationId: String?,
) : SignalREventConverter<SignalREvent.InvocationBindingFailure> {

    override fun convert(): SignalREvent.InvocationBindingFailure {
        return SignalREvent.InvocationBindingFailure(target, invocationId, null)
    }

    object Factory : SignalREventConverter.Factory<InvocationBindingFailureConverter> {
        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): InvocationBindingFailureConverter? {
            if (jsonObject.get("type")?.asInt == -1) {
                val target = jsonObject.get("target").asString
                val invocationId = jsonObject.get("invocationId")?.asString
                return InvocationBindingFailureConverter(target, invocationId)
            }
            return null
        }
    }

}