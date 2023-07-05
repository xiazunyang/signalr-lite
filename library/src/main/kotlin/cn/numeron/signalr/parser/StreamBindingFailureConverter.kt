package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class StreamBindingFailureConverter(
    private val invocationId: String?,
    private val exception: Exception?,
) : SignalREventConverter<SignalREvent.StreamBindingFailure> {

    override fun convert(): SignalREvent.StreamBindingFailure {
        return SignalREvent.StreamBindingFailure(invocationId, exception)
    }

    object Factory : SignalREventConverter.Factory<StreamBindingFailureConverter> {
        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): StreamBindingFailureConverter? {
            if (jsonObject.get("type")?.asInt == -2) {
                val invocationId = jsonObject.get("invocationId")?.asString
                return StreamBindingFailureConverter(invocationId, null)
            }
            return null
        }
    }

}