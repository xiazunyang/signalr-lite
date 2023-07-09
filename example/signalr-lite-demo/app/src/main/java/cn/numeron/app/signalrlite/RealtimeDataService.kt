package cn.numeron.app.signalrlite

import cn.numeron.signalr.SignalRInvocation

interface RealtimeDataService {

    @SignalRInvocation("Subscribe")
    suspend fun subscribe(tags: String)

}