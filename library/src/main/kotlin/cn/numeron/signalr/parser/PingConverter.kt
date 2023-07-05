package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class PingConverter : SignalREventConverter<SignalREvent.Ping> {

    override fun convert(): SignalREvent.Ping {
        return SignalREvent.Ping
    }

    object Factory : SignalREventConverter.Factory<PingConverter> {

        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): PingConverter? {
            if (jsonObject.get("type")?.asInt == 6) {
                return PingConverter()
            }
            return null
        }

    }
}