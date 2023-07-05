package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.JsonObject

class CloseConverter(
    private val error: String?,
    private val allowReconnect: Boolean,
) : SignalREventConverter<SignalREvent.Close> {

    override fun convert(): SignalREvent.Close {
        return SignalREvent.Close(
            error,
            allowReconnect
        )
    }

    object Factory : SignalREventConverter.Factory<CloseConverter> {
        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): CloseConverter? {
            if (jsonObject.get("type")?.asInt == 7) {
                val error = jsonObject.get("error")?.asString
                val allowReconnect = jsonObject.get("allowReconnect")?.asBoolean ?: false
                return CloseConverter(error, allowReconnect)
            }
            return null
        }
    }

}