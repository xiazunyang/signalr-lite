package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class UnknownConverter(private val jsonObject: JsonObject) : SignalREventConverter<SignalREvent.Unknown> {

    override fun convert(): SignalREvent.Unknown {
        return SignalREvent.Unknown(jsonObject)
    }

    object Factory : SignalREventConverter.Factory<UnknownConverter> {

        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): UnknownConverter {
            return UnknownConverter(jsonObject)
        }

    }

}