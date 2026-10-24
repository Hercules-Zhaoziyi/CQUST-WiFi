package top.msfxp.schedule.data.api

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// WebVPN 主机令牌算法与 CAS 密码加密工具
object CqustVpnCrypto {
    private const val VPN_PREFIX = "77726476706e69737468656265737421"
    private const val VPN_SECRET = "wrdvpnisthebest!"
    private const val CAS_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private val RANDOM = SecureRandom()

    // 常用主机令牌缓存表
    private val KNOWN_TOKENS = mapOf(
        "cas.cqust.edu.cn" to "77726476706e69737468656265737421f3f652d224217d436a468ca88d1b203b",
        "casp.cqust.edu.cn" to "77726476706e69737468656265737421f3f6528c693379456d1cc7a99c406d36f8",
        "web.cqust.edu.cn" to "77726476706e69737468656265737421e7f243d224217d436a468ca88d1b203b",
        "jwnew.cqust.edu.cn" to "77726476706e69737468656265737421fae04f99307e6b416b1b9de29d51367b4912",
        "sjjx.cqust.edu.cn" to "77726476706e69737468656265737421e3fd4b84693379456d1cc7a99c406d369b",
        "icon.cqust.edu.cn" to "77726476706e69737468656265737421f9f44e92693379456d1cc7a99c406d3652"
    )

    // 计算 WebVPN 目标主机名加密令牌
    fun vpnToken(hostname: String): String {
        KNOWN_TOKENS[hostname]?.let { return it }

        val cipher = Cipher.getInstance("AES/CFB/NoPadding")
        val keyBytes = VPN_SECRET.toByteArray(StandardCharsets.UTF_8)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(keyBytes)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val encrypted = cipher.doFinal(hostname.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder(VPN_PREFIX)
        for (b in encrypted) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // 生成 CAS 登录使用的随机字符串
    private fun randomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(CAS_CHARS[RANDOM.nextInt(CAS_CHARS.length)])
        }
        return sb.toString()
    }

    // CAS 密码加密
    fun encryptCasPassword(password: String, salt: String): String {
        val ivStr = randomString(16)
        val prefix = randomString(64)
        val plain = prefix + password

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keyBytes = salt.toByteArray(StandardCharsets.UTF_8)
        val ivBytes = ivStr.toByteArray(StandardCharsets.UTF_8)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    // 构造 WebVPN 代理地址
    fun buildVpnUrl(scheme: String, host: String, path: String): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "https://web.cqust.edu.cn/$scheme/${vpnToken(host)}$cleanPath"
    }
}
