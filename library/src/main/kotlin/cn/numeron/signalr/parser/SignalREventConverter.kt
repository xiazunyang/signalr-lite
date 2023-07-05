package cn.numeron.signalr.parser

import cn.numeron.signalr.SignalREvent
import cn.numeron.signalr.SignalRHub
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun interface SignalREventConverter<E : SignalREvent> {

    fun convert(): E

    fun interface Factory<C : SignalREventConverter<*>> {

        fun getConverter(jsonObject: JsonObject, signalRHub: SignalRHub): C?

    }

    companion object {

        private val jsonParser = JsonParser()

        private val converterFactories = mutableListOf<Factory<*>>(
            PingConverter.Factory,
            CompletionConverter.Factory,
            InvocationConverter.Factory,
            StreamBindingFailureConverter.Factory,
            InvocationBindingFailureConverter.Factory,
            HandshakeConverter.Factory,
            CloseConverter.Factory,
            UnknownConverter.Factory,
        )

        fun registerConverterFactory(factory: Factory<*>) {
            converterFactories.add(converterFactories.lastIndex - 1, factory)
        }

        fun convert(payload: String, signalRHub: SignalRHub): SignalREvent {
            val jsonObject = jsonParser.parse(payload).asJsonObject
            val signalREventConverter = converterFactories.firstNotNullOf {
                it.getConverter(jsonObject, signalRHub)
            }
            return signalREventConverter.convert()
        }

    }

}

