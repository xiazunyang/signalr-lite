package cn.numeron.app.signalrlite

import com.google.gson.annotations.SerializedName
import java.util.Date

data class RealtimeData(

    @SerializedName("Key")
    val key: String,

    @SerializedName("Value")
    val value: Double,

    @SerializedName("Group")
    val group: String,

    @SerializedName("OccurTime")
    val occurTime: Date

)