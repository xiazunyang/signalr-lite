package cn.numeron.app.signalrlite

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cn.numeron.app.signalrlite.ui.theme.SignalrlitedemoTheme
import cn.numeron.signalr.SignalRHub
import cn.numeron.signalr.SignalRInvocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
class MainActivity : ComponentActivity() {

    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val serverUrl = "http://api.dev.com/data-opening/signalr/realtime-data?__tenant=cbe81ce3-fdf5-ae08-7421-3a0955bd3378"

    private val signalRHub = SignalRHub.Builder(serverUrl)
        .invocationOwner(this)
        .build()

    private val realtimeDataService = signalRHub.simulate(RealtimeDataService::class.java)

    private var connectJob: Job? = null

    private var isConnected: Boolean by mutableStateOf(false)

    private var isSubscribed: Boolean by mutableStateOf(false)

    private var realtimeDataList: List<RealtimeData> by mutableStateOf(listOf())

    @SignalRInvocation("GetRealtime")
    fun onGotRealtimeData(data: RealtimeData) {
        realtimeDataList = realtimeDataList + data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SignalrlitedemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            for (realtimeData in realtimeDataList) {
                                item {
                                    ListItem(
                                        text = {
                                            Text(text = "${realtimeData.key}: ${realtimeData.value}")
                                        },
                                        secondaryText = {
                                            Text(text = formatter.format(realtimeData.occurTime))
                                        },
                                    )
                                    Divider()
                                }
                            }
                        }
                        Divider(modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                modifier = Modifier.width(160.dp),
                                onClick = ::onConnectButtonClick,
                            ) {
                                Text(text = if (isConnected) "DISCONNECT" else "CONNECT")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                modifier = Modifier.width(160.dp),
                                enabled = isConnected && !isSubscribed,
                                onClick = ::onSubscribeButtonClick,
                            ) {
                                Text(text = if (isSubscribed) "SUBSCRIBED" else "SUBSCRIBE")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    private fun onConnectButtonClick() {
        if (isConnected) {
            connectJob?.cancel()
        } else {
            connectJob = lifecycleScope.launch {
                try {
                    signalRHub.connect()
                } catch (throwable: CancellationException) {
                    Log.d("MainActivity", "signalr disconnected.", throwable)
                } catch (throwable: Throwable) {
                    Log.d("MainActivity", "connect error.", throwable)
                }
            }
            connectJob?.invokeOnCompletion {
                isConnected = false
                isSubscribed = false
            }
            isConnected = true
        }
    }

    private fun onSubscribeButtonClick() {
        lifecycleScope.launch {
            realtimeDataService.subscribe("data1,data2,data3,data4,data5")
            isSubscribed = true
        }
    }

}