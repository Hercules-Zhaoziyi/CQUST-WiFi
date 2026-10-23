package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.*
import java.util.Base64
import kotlin.concurrent.thread

class ProxyTest {
    private fun port():Int=ServerSocket(0).use{it.localPort}
    private fun server(mode:String="mixed"):Pair<LanProxy,Int>{
        val port=port()
        val proxy=LanProxy(ProxyOptions(port,mode,false,"local-user","test-password",false),"127.0.0.1",
            targetAllowed={it.isLoopbackAddress})
        proxy.start();return proxy to port
    }
    private fun headers(input:InputStream):String{
        val buffer=ByteArrayOutputStream()
        while(!buffer.toString("ISO-8859-1").endsWith("\r\n\r\n")){val b=input.read();check(b>=0);buffer.write(b)}
        return buffer.toString("ISO-8859-1")
    }
    private fun auth(socket:Socket){
        socket.soTimeout=3000;val input=DataInputStream(socket.getInputStream());val out=socket.getOutputStream()
        out.write(byteArrayOf(5,1,2));assertEquals(5,input.readUnsignedByte());assertEquals(2,input.readUnsignedByte())
        out.write(byteArrayOf(1,10)+"local-user".toByteArray()+byteArrayOf(13)+"test-password".toByteArray())
        assertEquals(1,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
    }
    private fun reply(input:DataInputStream):InetSocketAddress{
        assertEquals(5,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
        val kind=input.readUnsignedByte();val ip=ByteArray(if(kind==1)4 else 16);input.readFully(ip)
        return InetSocketAddress(InetAddress.getByAddress(ip),input.readUnsignedShort())
    }
    @Test fun missingAuthGets407(){
        val(proxy,port)=server()
        proxy.use{Socket("127.0.0.1",port).use{s->
            s.soTimeout=3000;s.getOutputStream().write("GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray())
            val h=headers(s.getInputStream());assertTrue(h.contains("407"));assertTrue(h.contains("Proxy-Authenticate: Basic"))
        }}
    }
    @Test fun socksTcpAndStop(){
        val origin=ServerSocket(0).apply{soTimeout=3000}
        val worker=thread(isDaemon=true){origin.use{val s=origin.accept();s.use{it.getOutputStream().write(it.getInputStream().read())}}}
        val(proxy,port)=server()
        val client=Socket("127.0.0.1",port)
        try{auth(client);val target=origin.localPort
            client.getOutputStream().write(byteArrayOf(5,1,0,1,127,0,0,1,(target shr 8).toByte(),target.toByte()))
            reply(DataInputStream(client.getInputStream()));client.getOutputStream().write(73)
            assertEquals(73,client.getInputStream().read())
        }finally{client.close();proxy.close();origin.close();worker.join(3000)}
        // Verify the actual listening port is released; a bare loopback connect
        // can self-connect when its ephemeral source port equals the closed port.
        ServerSocket().use{released->released.reuseAddress=true;released.bind(InetSocketAddress("127.0.0.1",port))}
    }
    @Test fun httpForwardStripsProxyCredentials(){
        val origin=ServerSocket(0).apply{soTimeout=3000};var observed=""
        val worker=thread(isDaemon=true){origin.use{it.accept().use{s->observed=headers(s.getInputStream());s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray())}}}
        val(proxy,port)=server("http")
        proxy.use{Socket("127.0.0.1",port).use{s->
            s.soTimeout=3000;val basic=Base64.getEncoder().encodeToString("local-user:test-password".toByteArray())
            s.getOutputStream().write("GET http://127.0.0.1:${origin.localPort}/demo HTTP/1.1\r\nProxy-Authorization: Basic $basic\r\n\r\n".toByteArray())
            assertTrue(headers(s.getInputStream()).contains("200 OK"))
            assertEquals('o'.code,s.getInputStream().read());assertEquals('k'.code,s.getInputStream().read())
        }}
        worker.join(3000);assertTrue(observed.startsWith("GET /demo HTTP/1.1"));assertFalse(observed.contains("proxy-authorization"))
    }
    @Test fun udpAssociate(){
        val echo=DatagramSocket(0,InetAddress.getLoopbackAddress());echo.soTimeout=3000
        val worker=thread(isDaemon=true){echo.use{val p=DatagramPacket(ByteArray(256),256);it.receive(p);it.send(p)}}
        val(proxy,port)=server("socks5")
        proxy.use{Socket("127.0.0.1",port).use{s->
            auth(s);s.getOutputStream().write(byteArrayOf(5,3,0,1,0,0,0,0,0,0))
            val relay=reply(DataInputStream(s.getInputStream()))
            DatagramSocket(0,InetAddress.getLoopbackAddress()).use{udp->
                udp.soTimeout=3000;val dest=echo.localPort
                val data=byteArrayOf(0,0,0,1,127,0,0,1,(dest shr 8).toByte(),dest.toByte())+"hello".toByteArray()
                udp.send(DatagramPacket(data,data.size,relay));val response=DatagramPacket(ByteArray(256),256);udp.receive(response)
                assertEquals("hello",String(response.data.copyOfRange(10,response.length)))
            }
        }}
        worker.join(3000)
    }
}
