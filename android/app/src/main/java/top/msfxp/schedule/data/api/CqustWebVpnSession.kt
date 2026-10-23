package top.msfxp.schedule.data.api

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

// WebVPN 网络会话管理器，维护 Cookie 与逐跳重定向
class CqustWebVpnSession(
    private val httpClient: HttpClient = defaultClient
) {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val VPN_GATEWAY = "https://web.cqust.edu.cn"
        const val VPN_TICKET_COOKIE = "wengine_vpn_ticketweb_cqust_edu_cn"

        val defaultClient by lazy {
            HttpClient(Android) {
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 6000
                    socketTimeoutMillis = 15000
                }
            }
        }
    }

    private val cookieMap = java.util.concurrent.ConcurrentHashMap<String, Cookie>()

    // 教务与实践系统基地址
    var eamsBaseUrl: String? = null
    var sjjxBaseUrl: String? = null

    // 检查是否已持有有效的 WebVPN 会话 Ticket
    fun hasVpnTicket(): Boolean = getCookie(VPN_TICKET_COOKIE)!=null

    // 获取特定 Cookie
    fun getCookie(key: String): String? = cookieMap.values.firstOrNull{it.name==key&&it.expiresAt>System.currentTimeMillis()}?.value

    // 解析并合并 Cookie
    fun updateCookies(response: HttpResponse) {
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie)
            ?: response.headers.getAll("Set-Cookie")
            ?: response.headers.getAll("set-cookie")
            ?: return
        for (header in setCookies) {
            val cookie=Cookie.parse(response.request.url.toString().toHttpUrl(),header)?:continue
            cookieMap["${cookie.domain}|${cookie.path}|${cookie.name}"]=cookie
        }
    }

    // 格式化当前会话 Cookie 请求头
    fun getCookieHeader(url:String): String {
        cookieMap.entries.removeAll{it.value.expiresAt<=System.currentTimeMillis()}
        return cookieMap.values.filter{it.matches(url.toHttpUrl())}.sortedByDescending{it.path.length}.joinToString("; ") { "${it.name}=${it.value}" }
    }
    private fun validate(url:String){val u=URI(url);require(u.scheme=="https"&&u.rawUserInfo==null&&u.host in listOf("web.cqust.edu.cn","authserver.cqust.edu.cn","sso.cqust.edu.cn","ids.cqust.edu.cn")){"统一认证跳转地址不受信任"}}

    // 将相对路径解析为完整 URL
    fun resolveUrl(currentUrl: String, location: String): String {
        return try {
            val baseUri = URI(currentUrl)
            baseUri.resolve(location).toString()
        } catch (_: Exception) {
            if (location.startsWith("http://") || location.startsWith("https://")) {
                location
            } else if (location.startsWith("/")) {
                val origin = try {
                    val uri = URI(currentUrl)
                    "${uri.scheme}://${uri.authority}"
                } catch (_: Exception) {
                    VPN_GATEWAY
                }
                "$origin$location"
            } else {
                val prefix = currentUrl.substringBeforeLast('/')
                "$prefix/$location"
            }
        }
    }

    // 执行带有自动 Cookie 与请求头的 GET 请求
    suspend fun get(
        url: String,
        referer: String? = null,
        isAjax: Boolean = false,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        validate(url)
        val resp = httpClient.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
            if (referer != null) header(HttpHeaders.Referrer, referer)
            if (isAjax) header("X-Requested-With", "XMLHttpRequest")
            headers.forEach { (k, v) -> header(k, v) }
            val cookies = getCookieHeader(url)
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
        }
        updateCookies(resp)
        return resp
    }

    // 执行带有自动 Cookie 与请求头的 POST 请求
    suspend fun post(
        url: String,
        body: String,
        referer: String? = null,
        isAjax: Boolean = false,
        contentType: String = ContentType.Application.FormUrlEncoded.toString(),
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        validate(url)
        val resp = httpClient.post(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
            if (referer != null) header(HttpHeaders.Referrer, referer)
            if (isAjax) header("X-Requested-With", "XMLHttpRequest")
            headers.forEach { (k, v) -> header(k, v) }
            header(HttpHeaders.ContentType, contentType)
            val cookies = getCookieHeader(url)
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
            setBody(body)
        }
        updateCookies(resp)
        return resp
    }

    // 自动跟随重定向直到终点或满足停止条件
    suspend fun getFollowingRedirects(
        startUrl: String,
        referer: String? = null,
        maxHops: Int = 10,
        stopOnUrlContains: String? = null
    ): Pair<String, HttpResponse> {
        var currentUrl = startUrl
        var currentReferer = referer
        var hops = 0

        var response = get(currentUrl, currentReferer)
        while (hops < maxHops) {
            hops++
            if (response.status == HttpStatusCode.Found ||
                response.status == HttpStatusCode.MovedPermanently ||
                response.status == HttpStatusCode.SeeOther ||
                response.status == HttpStatusCode.TemporaryRedirect
            ) {
                val location = response.headers[HttpHeaders.Location] ?: break
                val nextUrl = resolveUrl(currentUrl, location)

                if (stopOnUrlContains != null && nextUrl.contains(stopOnUrlContains)) {
                    return nextUrl to response
                }

                currentReferer = currentUrl
                currentUrl = nextUrl
                response = get(currentUrl, currentReferer)
            } else {
                break
            }
        }
        return currentUrl to response
    }
}
