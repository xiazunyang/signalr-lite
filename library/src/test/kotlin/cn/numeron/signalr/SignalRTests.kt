package cn.numeron.signalr

import cn.numeron.okhttp.log.LogLevel
import cn.numeron.okhttp.log.TextLogInterceptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test

class SignalRTests : TextLogInterceptor.Logger, SignalRLogger {

    @Test
    fun test() {
        val url = "http://api.dev.com/data-opening/signalr/realtime-data?__tenant=cbe81ce3-fdf5-ae08-7421-3a0955bd3378"
        val signalRHub = SignalRHub.Builder(url)
            .invocationOwner(this)
            .logger(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(TextLogInterceptor(this).setLevel(LogLevel.BODY))
                    .build()
            )
            .build()

        val realtimeDataService = signalRHub.simulate(RealtimeDataService::class.java)

        runBlocking {
            // websocket的生命周期与协程范围一致
            // The life cycle of websocket is the same as that of coroutine scope
            signalRHub.eventFlow.collect {
                if (it is SignalREvent.Handshake) {
                    // call simulated suspend method must launch a new coroutine job
                    launch {
                        realtimeDataService.subscribe("data1,data2,data3,data4,data5")
                    }
                }
            }
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