package cn.numeron.signalr

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.internal.bind.TypeAdapters
import com.google.gson.stream.JsonReader

class SignalREventDeserializer(

        private val gson: Gson,

        private val argumentsBinder: ArgumentsBinder

) {

    fun read(jsonReader: JsonReader): SignalREvent? {
        var type = 0
        var error: String? = null
        var target: String? = null
        var allowReconnect = false
        var invocationId: String? = null
        var resultOrItemJsonElement: JsonElement? = null
        var argumentsElements: List<JsonElement> = emptyList()

        jsonReader.beginObject()

        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                "type" -> type = jsonReader.nextInt()
                "error" -> error = jsonReader.nextString()
                "target" -> target = jsonReader.nextString()
                "allowReconnect" -> allowReconnect = jsonReader.nextBoolean()
                "invocationId" -> invocationId = jsonReader.nextString()
                "result", "item" -> {
                    resultOrItemJsonElement = TypeAdapters.JSON_ELEMENT.read(jsonReader)
                }
                "arguments" -> {
                    argumentsElements = buildList {
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            add(TypeAdapters.JSON_ELEMENT.read(jsonReader))
                        }
                        jsonReader.endArray()
                    }
                }
                else -> jsonReader.skipValue()
            }
        }

        jsonReader.endObject()

        jsonReader.close()
        return when (type) {
            1 -> {
                val parameterTypes = argumentsBinder.getParameterTypes(target!!)
                val parameterValues = parameterTypes.mapIndexed { index, clazz ->
                    gson.fromJson(argumentsElements[index], clazz)
                }
                SignalREvent.Invocation(target, parameterValues.toTypedArray(), invocationId)
            }
            3 -> {
                val returnType = argumentsBinder.getReturnType(invocationId!!)
                val returnValue = if (resultOrItemJsonElement != null && resultOrItemJsonElement !is JsonNull) {
                    gson.fromJson(resultOrItemJsonElement, returnType)
                } else null
                SignalREvent.Completion(invocationId, returnValue, error)
            }
            6 -> SignalREvent.Ping
            7 -> SignalREvent.Close(error, allowReconnect)
            else -> null
        }
    }

}