### SignalR Lite 

* 使用`kotlin`编写的，可以运行在`jvm/android`平台的`SignalR`简易客户端
* A simple SignalR client written in `kotlin` that can run on the `jvm/android` platform


* 与官方库相比，功能较为简单，仅支持本地与远程服务的相互调用。
* Compared with the official library, the function is relatively simple, only supports the mutual call of local and remote services.


* 因为仅使用了`kotlin coroutine`、`OkHttp`、`Gson`等常用库，所以相较于官方库，更加轻量。
* Because only `kotlin coroutine`, `OkHttp`, `Gson` libraries are used, it is lighter than the official library.

### Install
当前版本：[![](https://jitpack.io/v/cn.numeron/signalr-lite.svg)](https://jitpack.io/#cn.numeron/signalr-lite)

第1步. 添加JitPack仓库到你的项目中  
Step 1. Add the JitPack repository to your build file  
```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
第2步. 添加以下依赖到你的模块中  
Step 2. Add the dependency
```groovy
dependencies {
    ...
    implementation 'cn.numeron:signalr-lite:latest'
}
```

### Usage

* 查看[`MainActivity.kt`](https://github.com/xiazunyang/signalr-lite/blob/master/example/signalr-lite-demo/app/src/main/java/cn/numeron/app/signalrlite/MainActivity.kt)
* see [`MainActivity.kt`](https://github.com/xiazunyang/signalr-lite/blob/master/example/signalr-lite-demo/app/src/main/java/cn/numeron/app/signalrlite/MainActivity.kt)

1. 定义要模拟的远程方法  
   Define server methods for emulation
    ```kotlin
    interface RealtimeDataService {
        // Note: The simulated method must be suspend qualified
        @SignalRInvocation("Subscribe")
        suspend fun subscribe(tags: String)
    }
    ```

2. 在本地定义供服务器远程调用的方法  
   Define methods locally for the server to call remotely
   ```kotlin
   // Note: The 'SignalRInvocation' annotation is required.
   // If the remote method name is different from the local method name, you can specify it through the parameter
   @SignalRInvocation("GetRealtime")
   fun onGotRealtimeData(data: RealtimeData) {
       // This method is invoked during the remote service invocation
   }
    ```

3. 构建`SignalRHub`实例  
   Build `SignalRHub` instance
    ```kotlin
    private val signalRHub = SignalRHub.Builder(serverUrl)
        // Add object instance that defines local method
        .invocationOwner(this)
        .build()
    ```

4. 创建模拟服务器的实例  
   Create an instance of the simulated server
    ```kotlin
    private val realtimeDataService = signalRHub.simulate(RealtimeDataService::class.java)
    ```

5. 连接`SignalR`服务器  
   connect `SignalR` server.
    ```kotlin
    lifecycleScope.launch {
        try {
            signalRHub.connect()
        } catch (throwable: CancellationException) {
            Log.d("MainActivity", "signalr disconnected.", throwable)
        } catch (throwable: Throwable) {
            Log.d("MainActivity", "connect error.", throwable)
        }
    }
    ```

6. 调用远程方法  
   Call remote methods
    ```kotlin
     lifecycleScope.launch {
        realtimeDataService.subscribe("data1,data2,data3,data4,data5")
    }
    ```