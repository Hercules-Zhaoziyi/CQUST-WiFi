package cn.ziyis.cqustwifi

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VaultTest {
    @Test fun encryptedRoundTripAndCorruptionDoesNotOverwrite(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-vault-${System.nanoTime()}.enc"
        val file=File(context.noBackupFilesDir,name)
        try{
            val vault=Vault(context,name)
            vault.write(JSONObject().put("password","synthetic-test-secret"))
            assertEquals("synthetic-test-secret",vault.read().getString("password"))
            assertFalse(file.readText(Charsets.ISO_8859_1).contains("synthetic-test-secret"))
            file.writeBytes(ByteArray(24))
            assertThrows(Exception::class.java){vault.read()}
            assertThrows(IllegalStateException::class.java){vault.write(JSONObject())}
            assertArrayEquals(ByteArray(24),file.readBytes())
        }finally{file.delete();File(file.path+".bak").delete()}
    }
}
