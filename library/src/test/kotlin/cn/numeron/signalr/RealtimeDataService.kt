package cn.numeron.signalr

interface RealtimeDataService {

    @SignalRInvocation("Subscribe")
    fun subscribe(tags: String)

}