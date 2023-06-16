package cn.numeron.signalr

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.io.IOException
import java.io.StringReader
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SignalRHub private constructor(

    /** 连接地址 */
    private val url: String,

    /** Gson */
    private val gson: Gson,

    /** 调用方法的线程 */
    private val executor: Executor?,

    /** 日志记录器 */
    private val logger: SignalRLogger?,

    /** 授权地址 */
    private val authorization: String?,

    /** ping信号的间隔，单位为毫秒 */
    private val pingIntervalMillis: Long,

    /** OkHttpClient */
    private val okHttpClient: OkHttpClient,

    /** 拥有调用方法的对象 */
    private val invocationOwners: List<Any>

) {

    private val timer = Timer()

    private var webSocket: WebSocket? = null

    /** invocationId与返回值类型的绑定关系 */
    private val returnTypes = mutableMapOf<String, Class<*>>()

    /** 方法名称与参数类型列表的绑定关系 */
    private val argumentTypes = mutableMapOf<String, List<Class<*>>>()

    /** 方法名称与Method的绑定关系 */
    private val invocations = mutableMapOf<String, Pair<Any, Method>>()

    /** invocationId与回调的绑定关系 */
    private val callbacks = mutableMapOf<String, SignalREventCallback<*>>()

    private val signalREventDeserializer = SignalREventDeserializer(gson, object : ArgumentsBinder {
        override fun getReturnType(invocationId: String): Class<*> {
            return returnTypes[invocationId]!!
        }

        override fun getParameterTypes(target: String): List<Class<*>> {
            return argumentTypes[target]!!
        }
    })

    init {
        invocationOwners.asSequence()
            .map(Any::javaClass)
            .map(Class<Any>::getMethods)
            .flatMap(Array<Method>::toList)
            .filter(::isSignalRInvocationMethod)
            .forEach {
                val target = getTarget(it)
                val argumentTypes = it.parameterTypes.toList()
                this.argumentTypes[target] = argumentTypes
            }
    }

    val eventFlow = callbackFlow {

        val webSocketListener = object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger?.log("onOpen, response = $response")
                webSocket.send(HANDSHAKE)
                webSocket.send(PING)
                trySend(SignalREvent.Open)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                logger?.log("onFailure")
                webSocket.close(1000, null)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger?.log("onClosed, code: $code, reason: $reason")
                close()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                logger?.log("onMessage, bytes: ${bytes.size}")
                convertToEvent(bytes.asByteBuffer())?.forEach {
                    handleEventInternal(webSocket, it)
                    trySend(it)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                logger?.log("onMessage, text: $text")
                convertToEvent(ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)))?.forEach {
                    handleEventInternal(webSocket, it)
                    trySend(it)
                }
            }
        }
        webSocket = connectSignalR(url, webSocketListener)
        awaitClose {
            timer.cancel()
            webSocket?.close(1000, null)
            webSocket = null
        }
    }

    private suspend fun connectSignalR(url: String, listener: WebSocketListener): WebSocket {
        // 创建negotiate连接
        val negotiateHttpUrl = url.toHttpUrl().newBuilder().addEncodedPathSegment("negotiate").build()
        val negotiateRequest = Request.Builder()
            .url(negotiateHttpUrl)
            .post(byteArrayOf().toRequestBody())
            .apply {
                if (!authorization.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $authorization")
                }
            }
            .build()
        val negotiateCall = okHttpClient.newCall(negotiateRequest)
        val negotiateResponse = negotiateCall.await()
        val negotiateJson = negotiateResponse.body!!.string()
        val negotiatePayload = gson.fromJson(negotiateJson, NegotiatePayload::class.java)
        val connectionId = negotiatePayload.connectionId
        // 创建WebSocket连接
        val signalrHttpUrl = url.toHttpUrl().newBuilder().addQueryParameter("id", connectionId).build()
        val request = Request.Builder()
            .url(signalrHttpUrl)
            .apply {
                if (!authorization.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $authorization")
                }
            }
            .build()
        return okHttpClient.newWebSocket(request, listener)
    }

    private fun convertToEvent(buffer: ByteBuffer): List<SignalREvent>? {
        val payloadStr = if (buffer.isReadOnly) {
            val bufferBytes = ByteArray(buffer.remaining())
            buffer.get(bufferBytes, 0, bufferBytes.size)
            bufferBytes.toString(Charsets.UTF_8)
        } else {
            String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8)
        }
        // 消息载体为空
        if (payloadStr.isEmpty()) return null
        // 消息载体不完整
        if (payloadStr.substring(payloadStr.length - 1) != END_MARKER) {
            throw RuntimeException("Message is incomplete.")
        }
        // 转换为事件
        return payloadStr.split(END_MARKER)
            .asSequence()
            .filter(String::isNotEmpty)
            .map(::StringReader)
            .map(::JsonReader)
            .mapNotNull(signalREventDeserializer::read)
            .toList()
    }

    private fun findInvocation(target: String): Pair<Any, Method> {
        return invocations.getOrPut(target) {
            for (invocationOwner in invocationOwners) {
                val method = invocationOwner.javaClass.methods
                    .filter(::isSignalRInvocationMethod)
                    .find {
                        var name = it.getAnnotation(SignalRInvocation::class.java).alias
                        if (name.isEmpty()) {
                            name = it.name
                        }
                        name.equals(target, true)
                    }
                if (method != null) {
                    val pair = invocationOwner to method
                    invocations[target] = pair
                    return@getOrPut pair
                }
            }
            throw NullPointerException()
        }
    }

    private fun handleEventInternal(webSocket: WebSocket, event: SignalREvent) {
        when (event) {
            is SignalREvent.CancelInvocation -> {

            }

            is SignalREvent.Close -> {
                // 服务器要求客户端关闭连接
                webSocket.close(1000, null)
            }

            is SignalREvent.Completion -> {
                val invocationId = event.invocationId
                val callback = callbacks[invocationId]
                if (callback != null) {
                    if (event.error != null) {
                        callback.onFailure(event.error)
                    } else {
                        callback.onSuccess(event.result)
                    }
                }
            }

            is SignalREvent.Invocation -> {
                // 执行调用
                val (owner, method) = findInvocation(event.target)
                val invocationTask = InvocationTask(owner, method, event)
                if (executor == null) {
                    invocationTask.run()
                } else {
                    executor.execute(invocationTask)
                }
            }

            is SignalREvent.InvocationBindingFailure -> {

            }

            SignalREvent.Open -> {

            }

            SignalREvent.Ping -> {
                // 回ping消息
                timer.schedule(PingTask(), pingIntervalMillis)
            }

            is SignalREvent.StreamBindingFailure -> {

            }

            is SignalREvent.StreamInvocation -> {

            }

            is SignalREvent.StreamItem -> {

            }
        }
    }

    /** 发送事件 */
    fun send(signalREvent: SignalREvent) {
        val cmd = gson.toJson(signalREvent)
        logger?.log("call, invocation: $cmd")
        webSocket?.send(cmd + END_MARKER)
    }

    inline fun <reified T> connect(): T = connect(T::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T> connect(serviceClass: Class<T>): T {
        return Proxy.newProxyInstance(
            serviceClass.classLoader,
            arrayOf(serviceClass)
        ) { _, method: Method, args: Array<out Any> ->
            val target = getTarget(method)
            // 创建[CompletableFuture]对象，用于获取在当前线程等待回调结果
            val completableFuture = CompletableFuture<Any?>()

            val callback = object : SignalREventCallback<Any> {
                override fun onSuccess(value: Any?) {
                    completableFuture.complete(value)
                }

                override fun onFailure(error: String?) {
                    completableFuture.completeExceptionally(SignalRInvocationException(error))
                }
            }

            // 构建调用事件
            val invocationId = UUID.randomUUID().toString()
            val invocation = SignalREvent.Invocation(target, args, id = invocationId)
            // 保存回调与返回值类型
            callbacks[invocationId] = callback
            returnTypes[invocationId] = method.returnType
            // 发送SignalR事件
            send(invocation)
            completableFuture.join()
        } as T
    }

    private inner class PingTask : TimerTask() {
        override fun run() = send(SignalREvent.Ping)
    }

    private inner class InvocationTask(
        private val owner: Any,
        private val method: Method,
        private val invocation: SignalREvent.Invocation,
    ) : Runnable {

        override fun run() {
            // 执行
            val (result, error) = try {
                method.invoke(owner, *invocation.arguments) to null
            } catch (exception: Exception) {
                null to exception.message
            }
            // 如果有执行ID，则回传执行结果
            val invocationId = invocation.id
            if (invocationId != null) {
                send(SignalREvent.Completion(invocationId, result, error))
            }
        }
    }

    /** [SignalRHub]'s Builder */
    class Builder(private val url: String) {

        private var gson: Gson? = null
        private var executor: Executor? = null
        private var logger: SignalRLogger? = null
        private var authorization: String? = null
        private var okHttpClient: OkHttpClient? = null
        private var pingIntervalMillis: Long = 1000 * 6
        private val invocationOwners = mutableSetOf<Any>()

        /** Gson */
        fun gson(gson: Gson) = apply {
            this.gson = gson
        }

        /** OkHttpClient */
        fun okHttpClient(okHttpClient: OkHttpClient) = apply {
            this.okHttpClient = okHttpClient
        }

        /** 添加拥有调用方法的对象 */
        fun invocationOwner(owner: Any) = apply {
            this.invocationOwners.add(owner)
        }

        /** 设置日志记录器 */
        fun logger(logger: SignalRLogger) = apply {
            this.logger = logger
        }

        /** 设置ping信号的间隔 */
        fun pingInterval(millis: Long) = apply {
            this.pingIntervalMillis = millis
        }

        /** 设置线程池 */
        fun executor(executor: Executor) = apply {
            this.executor = executor
        }

        /** 设置执行远程调用的线程 */
        fun authorization(authorization: String) = apply {
            this.authorization = authorization
        }

        fun build() = SignalRHub(
            url = url,
            logger = logger,
            executor = executor,
            gson = gson ?: Gson(),
            authorization = authorization,
            pingIntervalMillis = pingIntervalMillis,
            invocationOwners = invocationOwners.toList(),
            okHttpClient = okHttpClient ?: OkHttpClient()
        )

    }

    companion object {
        private const val TAG = "SignalRHub"
        private const val END_MARKER = "\u001e"
        private const val PING = "{\"type\":6}$END_MARKER"
        private const val HANDSHAKE = "{\"protocol\":\"json\",\"version\":1}$END_MARKER"

        private suspend fun Call.await(): Response {
            return suspendCancellableCoroutine { continuation ->
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                })
            }
        }

        private fun isSignalRInvocationMethod(method: Method): Boolean {
            return method.getAnnotation(SignalRInvocation::class.java) != null
        }

        private fun getTarget(method: Method): String {
            var target = method.getAnnotation(SignalRInvocation::class.java).alias
            if (target.isEmpty()) {
                target = method.name
            }
            return target
        }
    }

}