package cn.numeron.signalr

import cn.numeron.okhttp.log.LogLevel
import cn.numeron.okhttp.log.TextLogInterceptor
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.StringReader

class SignalRTests : TextLogInterceptor.Logger, SignalRLogger {

    @Test
    fun test() {
        val url = "http://api.dev.com/data-opening/signalr/realtime-data?__tenant=cbe81ce3-fdf5-ae08-7421-3a0955bd3378"
        val authorization =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6IkM1RjQ0RUI1QjRCMEZGQTgxOTFFRjc1MDJBRkUzM0REIiwidHlwIjoiYXQrand0In0.eyJuYmYiOjE2ODY4MjQ1NDcsImV4cCI6MTcxODM2MDU0NywiaXNzIjoiaHR0cDovL3Nzby5kZXYuY29tIiwiYXVkIjoiQXV0aFNlcnZlciIsImNsaWVudF9pZCI6IkF1dGhTZXJ2ZXJfQXBwIiwic3ViIjoiYjIxOGJhNDEtNTU4Zi03MWQwLWMyMDMtM2EwOTU1ZDllYThlIiwiYXV0aF90aW1lIjoxNjg2ODE2MzQwLCJpZHAiOiJsb2NhbCIsInRlbmFudGlkIjoiY2JlODFjZTMtZmRmNS1hZTA4LTc0MjEtM2EwOTU1YmQzMzc4IiwiZW1haWwiOiJ4aWF6dW55YW5nQHN0YXJpbmdlcmEuY29tIiwicm9sZSI6WyJhZG1pbiIsIuePree7hOmVvyIsIuWRmOW3pSIsIuS4u-euoSJdLCJwaG9uZV9udW1iZXJfdmVyaWZpZWQiOiJGYWxzZSIsImVtYWlsX3ZlcmlmaWVkIjoiRmFsc2UiLCJlZGl0aW9uaWQiOiIyYzJlMzRhMy00Mzg0LWRlMDEtMjg4ZS0zYTA5MzczMDQ2YjUiLCJuYW1lIjoieGlhenVueWFuZyIsImlhdCI6MTY4NjgxNjM0MCwic2NvcGUiOlsiQXV0aFNlcnZlciIsIm9wZW5pZCIsInByb2ZpbGUiLCJvZmZsaW5lX2FjY2VzcyJdLCJhbXIiOlsicHdkIl19.MB99cdDAq5Y1cv6oWdfvXylYp-I0mSIPviIgqzISVzIN4yUJ2N3sr4ilemYslODRPSdd4fAiU_1tIkT9n3riREfLxEswtHWMmaSSHHQIxixIPLRmxc8bNilQgle0_jGAW9fFBdR2y3biWf7Lf79sFJ6LduaIucNtV-T_qAIXbp4GZH1pRkmbaDoV3iuVr9aGm8lfLtqADTMvltSSMsquhpglMrjikDjOVWS65i4cGv3t9E5hIKYAS7yJL5IQfTrSFARUQ2qnDPhO_8UpvJGzDimDZzNC-gtrw3btHi0vAPOKCNG3I4k0Wkc_JUTJw3C6aL8iOrXJLldW3Qyzj0Qj2g"
        val signalRHub = SignalRHub.Builder(url)
            .authorization(authorization)
            .invocationOwner(this)
            .logger(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(TextLogInterceptor(this).setLevel(LogLevel.BODY))
                    .build()
            )
            .build()

        val realtimeDataService = signalRHub.connect<RealtimeDataService>()

        runBlocking {
            // websocket的生命周期与协程范围一致
            // The life cycle of websocket is the same as that of coroutine scope
            signalRHub.eventFlow.collectLatest {
                if (it is SignalREvent.Open) {
                    val arguments = "data1,data2,data3,data4,data5"
                    val result = realtimeDataService.subscribe(arguments)
                    log("subscribe result: $result")
                }
            }
            log("SignalR disconnected.")
        }

    }

    override fun log(message: String) {
        println(message)
    }

    @SignalRInvocation("GetRealtime")
    fun onGotRealtimeData(data: RealtimeData) {
        log("on got realtime data: $data")
    }

}