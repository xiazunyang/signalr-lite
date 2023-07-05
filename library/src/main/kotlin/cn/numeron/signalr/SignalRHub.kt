package cn.numeron.signalr

import cn.numeron.signalr.parser.SignalREventConverter
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.coroutines.*

class SignalRHub private constructor(

    /** 连接地址 */
    private val url: String,

    /** Gson */
    internal val gson: Gson,

    /** 日志记录器 */
    internal val logger: SignalRLogger?,

    /** 授权地址 */
    private val authorization: String?,

    /** OkHttpClient */
    private val okHttpClient: OkHttpClient,

    /** 调用本地方法的高度器 */
    internal val invocationDispatcher: CoroutineDispatcher,

    /** 拥有调用方法的对象 */
    invocationOwners: List<Any>

) {

    /** invocationId与返回值类型的绑定关系 */
    internal val returnTypes = mutableMapOf<String, Class<*>>()

    /** 方法名称与参数类型列表的绑定关系 */
    internal val argumentTypes = mutableMapOf<String, List<Class<*>>>()

    /** 方法名称与Method的绑定关系 */
    private val invocations = mutableMapOf<String, Pair<Any, Method>>()

    /** invocationId与回调的绑定关系 */
    private val callbacks = mutableMapOf<String, SignalREventCallback<*>>()

    val messageChannel = Channel<SignalREvent>()

    val eventFlow = connectSignalR(url)
        .mapNotNull(::toSignalREvent)
        .onEach(::handleSignalREvent)

    init {
        for (owner in invocationOwners) {
            owner.javaClass
                .methods
                .filter(::isSignalRInvocationMethod)
                .forEach { method ->
                    val target = getTarget(method)
                    invocations[target] = owner to method
                    argumentTypes[target] = method.parameterTypes.toList()
                }
        }
    }

    private fun connectSignalR(url: String): Flow<WebSocketEvent> {
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
        val negotiateResponse = negotiateCall.execute()
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
        return okHttpClient.newWebSocketAsFlow(request, messageChannel.receiveAsFlow().map(::toMessage), logger)
    }

    private fun toSignalREvent(webSocketEvent: WebSocketEvent): SignalREvent? {
        when (webSocketEvent) {
            is WebSocketEvent.Open -> {
                return SignalREvent.Open
            }

            is WebSocketEvent.TextMessage -> {
                val message = webSocketEvent.text.substringBefore(END_MARKER)
                return SignalREventConverter.convert(message, this)
            }
        }
        return null
    }

    private suspend fun handleSignalREvent(event: SignalREvent) {
        when (event) {
            is SignalREvent.Open -> {
                // 尝试握手
                messageChannel.send(SignalREvent.Handshaking())
            }

            is SignalREvent.Close -> {
                // 服务器要求客户端关闭连接
                throw SignalRClosedException(event.error, event.allowReconnect)
            }

            is SignalREvent.Handshake, is SignalREvent.Ping -> {
                // 回ping消息
                messageChannel.send(SignalREvent.Ping)
            }

            is SignalREvent.Completion -> {
                // 远程方法调用完成
                val invocationId = event.invocationId
                val callback = callbacks.remove(invocationId) ?: return
                withContext(invocationDispatcher) {
                    if (event.error != null) {
                        callback.onFailure(event.error)
                    } else {
                        callback.onSuccess(event.result)
                    }
                }
            }

            is SignalREvent.Invocation -> {
                // 调用本地方法，并返回调用结果
                val (owner, method) = invocations[event.target] ?: return
                withContext(invocationDispatcher) {
                    // 执行
                    val (result, error) = try {
                        method.invoke(owner, *event.arguments.toTypedArray()) to null
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                        null to exception.message
                    }
                    // 如果有执行ID，则回传执行结果
                    val invocationId = event.id
                    if (invocationId != null) {
                        messageChannel.send(SignalREvent.Completion(invocationId, result, error))
                    }
                }
            }

            else -> Unit
        }
    }

    private fun toMessage(signalREvent: SignalREvent): String {
        return gson.toJson(signalREvent) + END_MARKER
    }

    fun invoke(target: String, vararg arguments: Any?) {
        messageChannel.trySendBlocking(
            SignalREvent.Invocation(target = target, arguments = arguments)
        )
    }

    fun <T> invoke(
        target: String,
        arguments: List<Any?>,
        returnType: Class<T>? = null,
        callback: SignalREventCallback<T>? = null
    ) {
        var convocationId: String? = null
        if (returnType != null && callback != null) {
            convocationId = UUID.randomUUID().toString()
            returnTypes[convocationId] = returnType
            callbacks[convocationId] = callback
        }
        messageChannel.trySendBlocking(
            SignalREvent.Invocation(
                target = target,
                arguments = arguments,
                id = convocationId
            )
        )
    }

    suspend fun <T> invoke(target: String, arguments: List<Any?>, returnType: Class<T>): T {
        return suspendCoroutine { continuation ->
            invoke(target, arguments, returnType, object : SignalREventCallback<T> {
                override fun onSuccess(value: T) {
                    continuation.resume(value)
                }

                override fun onFailure(error: String?) {
                    continuation.resumeWithException(SignalRInvocationException(error))
                }
            })
        }
    }

    suspend inline fun <reified T> invoke(target: String, vararg arguments: Any?): T? {
        return invoke(target, arguments.toList(), T::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> simulate(serviceClass: Class<T>): T {
        val proxyInstance = Proxy.newProxyInstance(
            serviceClass.classLoader,
            arrayOf(serviceClass)
        ) { _, method: Method, args: Array<out Any?> ->
            val target = getTarget(method)
            val returnType = method.returnType as Class<Any>
            val arguments = args.filterNot(Continuation::class.java::isInstance)
            val continuation = args.lastOrNull() as? Continuation<Any>
                ?: throw IllegalStateException("${serviceClass.simpleName}.${method.name} must be a suspend function.")
            val parameterTypes =
                arrayOf(String::class.java, List::class.java, Class::class.java, Continuation::class.java)
            javaClass.getMethod("invoke", *parameterTypes)
                .invoke(this, target, arguments, returnType, continuation)
        }
        return serviceClass.cast(proxyInstance)
    }

    /** [SignalRHub]'s Builder */
    class Builder(private val url: String) {

        private var gson: Gson? = null
        private var logger: SignalRLogger? = null
        private var authorization: String? = null
        private var okHttpClient: OkHttpClient? = null
        private val invocationOwners = mutableSetOf<Any>()
        private var coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default

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

        /** 设置线程池 */
        fun executor(dispatcher: CoroutineDispatcher) = apply {
            this.coroutineDispatcher = dispatcher
        }

        /** 设置执行远程调用的线程 */
        fun authorization(authorization: String) = apply {
            this.authorization = authorization
        }

        fun build() = SignalRHub(
            url = url,
            logger = logger,
            gson = gson ?: Gson(),
            authorization = authorization,
            invocationDispatcher = coroutineDispatcher,
            invocationOwners = invocationOwners.toList(),
            okHttpClient = okHttpClient ?: OkHttpClient()
        )

    }

    companion object {
        private const val TAG = "SignalRHub"
        private const val END_MARKER = "\u001e"

        private fun isSignalRInvocationMethod(method: Method): Boolean {
            return method.getAnnotation(SignalRInvocation::class.java) != null
        }

        @JvmStatic
        fun getTarget(method: Method): String {
            var target = method.getAnnotation(SignalRInvocation::class.java).alias
            if (target.isEmpty()) {
                target = method.name
            }
            return target
        }
    }

}