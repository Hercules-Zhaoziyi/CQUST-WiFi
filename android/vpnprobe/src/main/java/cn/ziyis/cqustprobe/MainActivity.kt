package cn.ziyis.cqustprobe

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/** CI-only ordinary application UID. Never bundled with the user APK. */
class MainActivity:Activity(){
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val text=TextView(this);setContentView(text)
        File(filesDir,"result.txt").delete()
        Thread{
            val result=runCatching{
                val connection=URL("http://cqust-fixture.invalid/").openConnection() as HttpURLConnection
                connection.connectTimeout=7000;connection.readTimeout=7000
                try{connection.inputStream.bufferedReader().use{it.readText()}}finally{connection.disconnect()}
            }.getOrElse{"PROBE_ERROR:${it.javaClass.simpleName}"}
            File(filesDir,"result.txt").writeText(result)
            runOnUiThread{text.text=result}
        }.start()
    }
}
