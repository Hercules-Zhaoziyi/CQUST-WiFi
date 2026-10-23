package cn.ziyis.cqustwifi

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.*
import java.io.DataInputStream
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit

/** App-owned HTTPS traffic can use the desktop SOCKS endpoint too.
 * The VPN excludes this UID to avoid native loops; campus authentication keeps
 * its separate CampusEngine Wi-Fi client. TLS verification is never disabled.
 */
object ApplicationClients {
    fun client():OkHttpClient {
        val builder=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).callTimeout(20,TimeUnit.SECONDS)
        if(Repository.state.value.vpn){
            val p=Repository.vpnProfile();val wifi=Repository.engine.wifi()
            if(wifi!=null&&Protocol.privateV4(p.host)){
                builder.dns(object:Dns{override fun lookup(hostname:String)=listOf(InetAddress.getByAddress(hostname,byteArrayOf(198.toByte(),18,0,99)))})
                builder.socketFactory(object:SocketFactory(){
                    override fun createSocket():Socket=object:Socket(){
                        override fun connect(endpoint:SocketAddress,timeout:Int){
                            val target=endpoint as InetSocketAddress
                            if(!isBound)super.bind(InetSocketAddress(0))
                            wifi.bindSocket(this)
                            super.connect(InetSocketAddress(p.host,p.port),if(timeout>0)timeout else 8000)
                            soTimeout=10000
                            val input=DataInputStream(super.getInputStream());val out=super.getOutputStream()
                            out.write(byteArrayOf(5,1,2));check(input.readUnsignedByte()==5&&input.readUnsignedByte()==2)
                            val user=p.username.toByteArray();val password=p.password.toByteArray()
                            require(user.size in 1..255&&password.size in 1..255)
                            try{out.write(byteArrayOf(1,user.size.toByte())+user+byteArrayOf(password.size.toByte())+password)}finally{password.fill(0)}
                            check(input.readUnsignedByte()==1&&input.readUnsignedByte()==0)
                            val domain=target.hostString.toByteArray(Charsets.UTF_8);require(domain.size in 1..255)
                            out.write(byteArrayOf(5,1,0,3,domain.size.toByte())+domain+byteArrayOf((target.port shr 8).toByte(),target.port.toByte()))
                            check(input.readUnsignedByte()==5&&input.readUnsignedByte()==0);input.readUnsignedByte()
                            val size=when(input.readUnsignedByte()){1->4;4->16;3->input.readUnsignedByte();else->error("SOCKS 地址无效")}
                            input.readFully(ByteArray(size));input.readUnsignedShort()
                        }
                    }
                    override fun createSocket(host:String,port:Int)=createSocket().apply{connect(InetSocketAddress.createUnresolved(host,port))}
                    override fun createSocket(host:InetAddress,port:Int)=createSocket(host.hostName,port)
                    override fun createSocket(host:String,port:Int,local:InetAddress,localPort:Int)=createSocket().apply{bind(InetSocketAddress(local,localPort));connect(InetSocketAddress.createUnresolved(host,port))}
                    override fun createSocket(host:InetAddress,port:Int,local:InetAddress,localPort:Int)=createSocket(host.hostName,port,local,localPort)
                })
            }
        }
        return builder.build()
    }
}
