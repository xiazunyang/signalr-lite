package cn.numeron.signalr

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

sealed class SignalREvent(

    val type: Int

) {

    class StreamBindingFailure(

        val invocationId: String?,

        val exception: Exception?

    ) : SignalREvent(-2)

    class InvocationBindingFailure(

        val target: String,

        val invocationId: String?,

        val exception: Exception?

    ) : SignalREvent(-1)

    /** 调用请求 */
    class Invocation(

        val target: String,

        val arguments: List<Any?>,

        @SerializedName("invocationId")
        val id: String? = null,

        val streamIds: List<String>? = null

    ) : SignalREvent(1) {

        constructor(target: String, vararg arguments: Any?) : this(target, arguments.toList())

        override fun toString(): String = buildString {
            append("target: $target, arguments: $arguments")
            if (!id.isNullOrEmpty()) {
                append(", id: $id")
            }
            if (!streamIds.isNullOrEmpty()) {
                append(", streamIds: $streamIds")
            }
        }

    }

    class StreamItem(

        val invocationId: String,

        val item: Any?,

        val headers: Map<String, String>?

    ) : SignalREvent(2)

    class Completion(

        val invocationId: String,

        /** 调用结果 */
        val result: Any?,

        /** 错误信息 */
        val error: String?

    ) : SignalREvent(3)

    class StreamInvocation(

        val headers: Map<String, String>?,

        val invocationId: String?,

        val target: String,

        val arguments: Array<Any>,

        val streamIds: List<String>?

    ) : SignalREvent(4)

    class CancelInvocation(

        val invocationId: String,

        val headers: Map<String, String>?

    ) : SignalREvent(5)

    /** 在线信号 */
    object Ping : SignalREvent(6) {
        override fun toString() = "SignalR ping"
    }

    /** 连接关闭 */
    class Close(

        /** 错误信息 */
        val error: String?,

        /** 是否允许重新连接 */
        val allowReconnect: Boolean = false

    ) : SignalREvent(7) {
        override fun toString() = "SignalR close"
    }

    /** 连接成功 */
    object Open : SignalREvent(0) {
        override fun toString(): String = "SignalR open"
    }

    /** 握手消息 */
    class Handshaking(

        val protocol: String = "json",

        val version: Int = 1

    ) : SignalREvent(0)

    object Handshake : SignalREvent(0) {
        override fun toString() = "handshake"
    }

    /** 未处理的事件 */
    class Unknown(
        /** 消息json */
        val payload: JsonObject
    ) : SignalREvent(0) {
        override fun toString(): String = payload.toString()
    }

}
