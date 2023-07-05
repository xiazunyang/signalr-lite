package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject

class HandshakeConverter : SignalREventConverter<SignalREvent.Handshake> {

    override fun convert(): SignalREvent.Handshake {
        return SignalREvent.Handshake
    }

    object Factory : SignalREventConverter.Factory<HandshakeConverter> {

        override fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): HandshakeConverter? {
            if (jsonObject.size() == 0) {
                return HandshakeConverter()
            }
            return null
        }

    }
}