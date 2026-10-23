package cn.ziyis.cqustwifi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Whole credential document encrypted by an app-private Android Keystore key. */
class Vault(context:Context,name:String="credentials.enc") {
    private val file=AtomicFile(File(context.noBackupFilesDir,name))
    private val alias="cqust-wifi-android-v1"
    private var unreadable=false
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read():JSONObject {
        if(!file.baseFile.exists())return JSONObject()
        unreadable=true
        val bytes=file.readFully();require(bytes.size>13){"凭据文件无效"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)).also{unreadable=false}
    }
    @Synchronized fun write(value:JSONObject) {
        check(!unreadable){"原凭据暂不可读，已阻止覆盖，请先恢复设备密钥。"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val plain=value.toString().toByteArray();val out=file.startWrite()
        try {out.write(cipher.iv);out.write(cipher.doFinal(plain));file.finishWrite(out)}
        catch(t:Throwable){file.failWrite(out);throw t}finally{plain.fill(0)}
    }
}
