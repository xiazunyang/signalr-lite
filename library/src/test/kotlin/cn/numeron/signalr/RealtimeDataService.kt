package cn.numeron.signalr

interface RealtimeDataService {

    @SignalRInvocation("Subscribe")
    suspend fun subscribe(tags: String)

}