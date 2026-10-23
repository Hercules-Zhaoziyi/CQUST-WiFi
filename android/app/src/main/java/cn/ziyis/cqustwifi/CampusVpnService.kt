package cn.ziyis.cqustwifi

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.*
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import hev.htproxy.TProxyService
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class CampusVpnService:VpnService(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var tun:ParcelFileDescriptor?=null
    private val started=AtomicBoolean(false)
    private var network:Network?=null
    private var config:File?=null
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onLost(net:Network){if(net==network){Repository.vpnState(false,"Wi-Fi 已断开，VPN 已停止");stopSelf()}}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){Repository.vpnStoppedByUser();stopSelf();return START_NOT_STICKY}
        if(!started.compareAndSet(false,true))return START_NOT_STICKY
        try {
            ServiceCompat.startForeground(this,20,Notifications.build(this,"正在连接电脑 SOCKS5",true),
                if(android.os.Build.VERSION.SDK_INT>=34)ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }catch(_:Exception){Repository.vpnState(false,"系统拒绝启动 VPN 前台服务");stopSelf();return START_NOT_STICKY}
        scope.launch {
            try {
                require(!Repository.state.value.sharing){"请先关闭本机共享代理。"}
                val profile=Repository.vpnProfile()
                require(Protocol.privateV4(profile.host)&&profile.port in 1024..65535){"请先填写电脑代理信息。"}
                require(profile.username.isNotBlank()&&profile.password.isNotBlank()){"代理账号密码未保存。"}
                require(profile.username.toByteArray().size<=255&&profile.password.toByteArray().size<=255)
                val wifi=Repository.engine.wifi()?:error("请先连接可以访问电脑的校园 Wi-Fi。")
                network=wifi
                // Explicitly verify RFC 1929 auth through the Wi-Fi before creating TUN.
                wifi.socketFactory.createSocket().use{socket->
                    socket.connect(InetSocketAddress(profile.host,profile.port),5000);socket.soTimeout=5000
                    val output=socket.getOutputStream();val input=DataInputStream(socket.getInputStream())
                    output.write(byteArrayOf(5,1,2))
                    require(input.readUnsignedByte()==5&&input.readUnsignedByte()==2){"电脑代理未接受密码认证。"}
                    val user=profile.username.toByteArray();val password=profile.password.toByteArray()
                    try{output.write(byteArrayOf(1,user.size.toByte())+user+byteArrayOf(password.size.toByte())+password)}
                    finally{password.fill(0)}
                    require(input.readUnsignedByte()==1&&input.readUnsignedByte()==0){"电脑代理拒绝账号密码，请核对。"}
                }
                val cm=Repository.engine.connectivity
                // Native engine sockets share our UID; exclude our package to avoid loops,
                // and bind the process to Wi-Fi so native sockets cannot fall back to 5G.
                check(cm.bindProcessToNetwork(wifi)){"无法绑定校园 Wi-Fi。"}
                setUnderlyingNetworks(arrayOf(wifi))
                val builder=Builder().setSession("CQUST Wi-Fi · 电脑代理").setMtu(1500)
                    .addAddress("198.18.0.1",30).addAddress("fd00:4351::1",126)
                    .addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
                    .addDisallowedApplication(packageName).setBlocking(false)
                val descriptor=builder.establish()?:error("VPN 授权已取消，请重新确认。")
                tun=descriptor
                // Engine requires a config file. It exists only in noBackupFilesDir,
                // never shared, included in logs, or copied into exported sources.
                val file=File(noBackupFilesDir,"active-tunnel.yml");config=file
                file.writeText("""
                    tunnel:
                      mtu: 1500
                      ipv4: 198.18.0.1
                      ipv6: 'fd00:4351::1'
                    socks5:
                      address: ${Protocol.yaml(profile.host)}
                      port: ${profile.port}
                      username: ${Protocol.yaml(profile.username)}
                      password: ${Protocol.yaml(profile.password)}
                      udp: 'udp'
                    misc:
                      log-level: error
                      connect-timeout: 10000
                      tcp-read-write-timeout: 300000
                      udp-read-write-timeout: 60000
                """.trimIndent())
                file.setReadable(false,false);file.setWritable(false,false);file.setReadable(true,true);file.setWritable(true,true)
                check(TProxyService.TProxyStartService(file.absolutePath,descriptor.fd)){"转发核心未能启动。"}
                cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback)
                Repository.vpnState(true,"VPN 已启动 · 电脑代理认证通过")
                Repository.log("已启动电脑 SOCKS5 转发；本应用认证请求仍直连 Wi-Fi。")
                while(isActive){
                    delay(1000)
                    if(!TProxyService.TProxyIsRunning())error("转发核心已停止，请检查电脑代理。")
                    val stats=TProxyService.TProxyGetStats()
                    Repository.vpnState(true,"VPN 已启动 · 电脑代理认证通过",
                        "上传 ${formatBytes(stats.getOrElse(1){0})} · 下载 ${formatBytes(stats.getOrElse(3){0})}")
                }
            }catch(e:CancellationException){throw e}
            catch(e:Throwable){
                Repository.vpnState(false,if(e is IllegalArgumentException||e is IllegalStateException)
                    e.message.orEmpty()else "连接未完成，请检查电脑代理、Wi-Fi 和账号密码。")
                Repository.log("电脑代理连接已停止。");stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    override fun onRevoke(){Repository.vpnStoppedByUser();stopSelf();super.onRevoke()}
    override fun onDestroy(){
        scope.cancel();runCatching{Repository.engine.connectivity.unregisterNetworkCallback(callback)}
        Repository.vpnState(false,"电脑代理已断开")
        // Native quit can join its worker thread: do not block the UI thread.
        Thread{
            runCatching{TProxyService.TProxyStopService()}
            runCatching{tun?.close()};tun=null
            config?.delete();Repository.engine.connectivity.bindProcessToNetwork(null)
        }.start()
        super.onDestroy()
    }
}

fun formatBytes(bytes:Long):String=when{
    bytes<1024->"$bytes B";bytes<1024*1024->"%.1f KiB".format(bytes/1024.0)
    else->"%.1f MiB".format(bytes/1048576.0)
}
