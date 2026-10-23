package cn.ziyis.cqustwifi

import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Authenticated explicit proxy, not an interceptor. All targets are DNS-checked. */
class LanProxy(
    private val options:ProxyOptions,private val bind:String,
    private val resolve:(String)->List<InetAddress> = {InetAddress.getAllByName(it).toList()},
    private val connect:(SocketAddress)->Socket = {Socket().apply{connect(it,5000)}},
    private val bindUdp:(DatagramSocket)->Unit = {},
    private val targetAllowed:(InetAddress)->Boolean = Protocol::publicTarget
):Closeable {
    private val pool=Executors.newCachedThreadPool()
    private val sockets=ConcurrentHashMap.newKeySet<Closeable>()
    private val slots=Semaphore(32)
    private var listener:ServerSocket?=null
    val up=AtomicLong();val down=AtomicLong()
    fun start(){
        require(options.port in 1024..65535)
        require(options.mode in listOf("mixed","http","socks5"))
        require(options.username.isNotBlank()&&options.username.toByteArray().size<=255&&
            !options.username.contains(':')&&options.username.none{it.isISOControl()})
        require(if(options.campusPassword)options.password.isNotEmpty()&&options.password.toByteArray().size<=255
            else options.password.length in 8..64&&options.password.all{it.code in 33..126})
        val server=ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(bind,options.port))}
        listener=server;sockets.add(server)
        pool.execute{
            while(!server.isClosed) {
                val client=runCatching{server.accept()}.getOrNull()?:break
                val peer=client.inetAddress
                val allowed=if(bind=="127.0.0.1")peer.isLoopbackAddress else Protocol.privateV4(peer.hostAddress.orEmpty())
                if(!allowed||!slots.tryAcquire()){client.close();continue}
                sockets.add(client);client.soTimeout=10000
                pool.execute{try{
                    val input=PushbackInputStream(client.getInputStream(),1);val first=input.read()
                    require(first>=0);input.unread(first)
                    if(first==5&&options.mode!="http")socks(client,DataInputStream(input))
                    else if(first!=5&&options.mode!="socks5")http(client,input)
                }catch(_:Exception){}finally{runCatching{client.close()};sockets.remove(client);slots.release()}}
            }
        }
    }
    private fun credentials(user:ByteArray,pass:ByteArray):Boolean =
        MessageDigest.isEqual(user,options.username.toByteArray()) and MessageDigest.isEqual(pass,options.password.toByteArray())
    private fun readBytes(input:DataInputStream,n:Int)=ByteArray(n).also{input.readFully(it)}
    private fun addresses(host:String,port:Int):List<InetSocketAddress> {
        require(host.isNotBlank()&&host.length<=253&&host.none{it.isWhitespace()||it.isISOControl()})
        require(port in 1..65535&&port !in listOf(25,465,587))
        val ips=resolve(host).take(32);require(ips.isNotEmpty()&&ips.all(targetAllowed)){"Target denied"}
        return ips.map{InetSocketAddress(it,port)}
    }
    private fun remote(host:String,port:Int):Socket {
        for(address in addresses(host,port)){
            val s=runCatching{connect(address)}.getOrNull()?:continue
            sockets.add(s);s.soTimeout=3600000;return s
        }
        error("Target unavailable")
    }
    private fun address(input:DataInputStream):Pair<String,Int>{
        val host=when(input.readUnsignedByte()){
            1->InetAddress.getByAddress(readBytes(input,4)).hostAddress!!
            4->InetAddress.getByAddress(readBytes(input,16)).hostAddress!!
            3->String(readBytes(input,input.readUnsignedByte()),Charsets.UTF_8)
            else->error("Address type")
        };return host to input.readUnsignedShort()
    }
    private fun encoded(address:InetSocketAddress):ByteArray {
        val ip=address.address.address
        return byteArrayOf(if(ip.size==4)1 else 4)+ip+byteArrayOf((address.port shr 8).toByte(),address.port.toByte())
    }
    private fun socks(client:Socket,input:DataInputStream) {
        val out=client.getOutputStream();require(input.readUnsignedByte()==5)
        val methods=readBytes(input,input.readUnsignedByte())
        if(!methods.contains(2.toByte())){out.write(byteArrayOf(5,255.toByte()));return}
        out.write(byteArrayOf(5,2));require(input.readUnsignedByte()==1)
        val user=readBytes(input,input.readUnsignedByte());val password=readBytes(input,input.readUnsignedByte())
        val valid=credentials(user,password);password.fill(0)
        out.write(byteArrayOf(1,if(valid)0 else 1));if(!valid)return
        require(input.readUnsignedByte()==5);val command=input.readUnsignedByte();require(input.readUnsignedByte()==0)
        val(host,port)=address(input)
        if(command==3) {
            val requested=InetAddress.getByName(host)
            require(requested.isAnyLocalAddress||requested==client.inetAddress)
            udp(client,input,port);return
        }
        if(command!=1){out.write(byteArrayOf(5,7,0,1,0,0,0,0,0,0));return}
        val server=try{remote(host,port)}catch(_:Exception){out.write(byteArrayOf(5,2,0,1,0,0,0,0,0,0));return}
        out.write(byteArrayOf(5,0,0)+encoded(server.localSocketAddress as InetSocketAddress))
        client.soTimeout=3600000;relay(client,server,input)
    }
    private fun http(client:Socket,input:InputStream) {
        val bytes=ByteArrayOutputStream()
        while(bytes.size()<16384) {
            val b=input.read();require(b>=0);bytes.write(b)
            val a=bytes.toByteArray()
            if(a.size>=4&&a.takeLast(4)==listOf(13.toByte(),10.toByte(),13.toByte(),10.toByte()))break
        }
        require(bytes.size()<16384)
        val lines=bytes.toString("ISO-8859-1").split("\r\n")
        val request=lines.first().split(' ');require(request.size==3&&request[2] in listOf("HTTP/1.0","HTTP/1.1"))
        require(request[0] in listOf("CONNECT","GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS"))
        val headers=lines.drop(1).filter{it.isNotEmpty()}.map {
            require(!it.startsWith(' ')&&!it.startsWith('\t')&&it.none{c->c.isISOControl()&&c!='\t'})
            val colon=it.indexOf(':');require(colon>0)
            it.substring(0,colon).lowercase() to it.substring(colon+1).trim()
        }
        val auth=headers.filter{it.first=="proxy-authorization"};require(auth.size<=1)
        val token=auth.firstOrNull()?.second?.split(' ',limit=2)
        val decoded=if(token?.size==2&&token[0].equals("Basic",true))
            runCatching{Base64.getDecoder().decode(token[1])}.getOrNull()else null
        val colon=decoded?.indexOf(':'.code.toByte())?:-1
        val valid=decoded!=null&&colon>=0&&credentials(decoded.copyOfRange(0,colon),decoded.copyOfRange(colon+1,decoded.size))
        decoded?.fill(0)
        if(!valid){client.getOutputStream().write(("HTTP/1.1 407 Proxy Authentication Required\r\n"+
            "Proxy-Authenticate: Basic realm=\"CQUST Wi-Fi\", charset=\"UTF-8\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray());return}
        require(headers.count{it.first=="content-length"}<=1&&headers.count{it.first=="transfer-encoding"}<=1)
        require(!(headers.any{it.first=="content-length"}&&headers.any{it.first=="transfer-encoding"}))
        val tunnel=request[0]=="CONNECT"
        val uri=URI(if(tunnel)"http://"+request[1]else request[1])
        require(uri.scheme=="http"&&uri.host!=null&&uri.rawUserInfo==null&&uri.fragment==null)
        if(tunnel)require(uri.port>0&&uri.rawQuery==null&&uri.path.isNullOrEmpty())
        val server=try{remote(uri.host,if(uri.port>0)uri.port else 80)}
            catch(_:Exception){client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());return}
        if(tunnel)client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        else {
            val named=headers.filter{it.first=="connection"}.flatMap{it.second.split(',').map(String::trim)}
            val remove=setOf("proxy-authorization","proxy-connection","connection","host","keep-alive","upgrade")+named
            val path=uri.rawPath.ifEmpty{"/"}+(uri.rawQuery?.let{"?$it"}?:"")
            val host=if(uri.host.contains(':'))"[${uri.host}]"else uri.host
            val head=buildString{
                append("${request[0]} $path HTTP/1.1\r\nHost: $host:${if(uri.port>0)uri.port else 80}\r\nConnection: close\r\n")
                headers.filter{it.first !in remove}.forEach{append("${it.first}: ${it.second}\r\n")}
                append("\r\n")
            };server.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1))
        }
        client.soTimeout=3600000;relay(client,server,input)
    }
    private fun relay(client:Socket,server:Socket,input:InputStream) {
        val upload=pool.submit{runCatching{
            val b=ByteArray(16384);while(true){val n=input.read(b);if(n<0)break;server.getOutputStream().write(b,0,n);up.addAndGet(n.toLong())};server.shutdownOutput()
        }}
        try {
            val b=ByteArray(16384);while(true){val n=server.getInputStream().read(b);if(n<0)break;client.getOutputStream().write(b,0,n);down.addAndGet(n.toLong())}
        }finally{runCatching{server.close()};runCatching{client.close()};upload.cancel(true);sockets.remove(server)}
    }
    private fun udp(client:Socket,input:DataInputStream,requestedPort:Int) {
        val relay=DatagramSocket(InetSocketAddress(bind,0));sockets.add(relay);bindUdp(relay);relay.soTimeout=500
        val live=AtomicBoolean(true);var port=requestedPort.takeIf{it>0}
        val targets=mutableSetOf<InetSocketAddress>()
        client.getOutputStream().write(byteArrayOf(5,0,0)+encoded(relay.localSocketAddress as InetSocketAddress))
        client.soTimeout=3600000
        val control=pool.submit{try{input.read()}finally{live.set(false)}}
        val deadline=System.nanoTime()+TimeUnit.HOURS.toNanos(1)
        try {while(live.get()&&!relay.isClosed&&System.nanoTime()<deadline){
            val packet=DatagramPacket(ByteArray(65507),65507)
            try{relay.receive(packet)}catch(_:SocketTimeoutException){continue}
            val from=packet.socketAddress as InetSocketAddress
            if(from.address==client.inetAddress&&(port==null||port==from.port)){
                val stream=DataInputStream(ByteArrayInputStream(packet.data,0,packet.length))
                if(stream.readUnsignedShort()!=0||stream.readUnsignedByte()!=0)continue
                val(host,destinationPort)=runCatching{address(stream)}.getOrNull()?:continue
                val destination=runCatching{addresses(host,destinationPort).first()}.getOrNull()?:continue
                if(targets.size>=128&&destination !in targets)continue
                val data=stream.readBytes();port=from.port;targets.add(destination)
                relay.send(DatagramPacket(data,data.size,destination));up.addAndGet(data.size.toLong())
            }else if(from in targets&&port!=null){
                val data=byteArrayOf(0,0,0)+encoded(from)+packet.data.copyOf(packet.length)
                if(data.size<=65507){relay.send(DatagramPacket(data,data.size,client.inetAddress,port!!));down.addAndGet(packet.length.toLong())}
            }
        }}finally{relay.close();sockets.remove(relay);control.cancel(true)}
    }
    override fun close(){runCatching{listener?.close()};listener=null;sockets.toList().forEach{runCatching{it.close()}};sockets.clear();pool.shutdownNow()}
}
