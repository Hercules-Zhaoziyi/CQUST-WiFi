package top.msfxp.schedule.data.api

import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

// 统一身份认证登录结果
data class CasAuthResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val statusCode: Int? = null,
    val isNetworkError: Boolean = false,
    val isNeedCaptcha: Boolean = false,
    val session: CqustWebVpnSession? = null
)

// 统一身份认证客户端
object CqustCasAuthClient {
    private const val GATEWAY = "https://web.cqust.edu.cn"
    private const val JWNEW_HOST = "jwnew.cqust.edu.cn"

    // 建立教务会话（通过统一身份认证与 WebVPN 单点登录）
    suspend fun connect(
        studentId: String,
        passwordRaw: String,
        onProgress: ((String) -> Unit)? = null
    ): Result<CqustWebVpnSession> {
        // 1. 统一身份认证登录
        onProgress?.invoke("正在登录统一身份认证")
        val casResult = login(studentId, passwordRaw)
        if (!casResult.success || casResult.session == null) {
            val errMsg = casResult.errorMessage ?: "认证失败，请检查账号密码或网络连接"
            return Result.failure(Exception(errMsg))
        }

        // 2. 接入教务系统
        onProgress?.invoke("正在接入教务系统")
        val session = casResult.session
        val vpnEamsBase = CqustVpnCrypto.buildVpnUrl("http", JWNEW_HOST, "/eams")
        val unifiedLoginUrl = "$vpnEamsBase/unifiedLogin.action"

        return try {
            val (homeUrl, ssoResp) = session.getFollowingRedirects(unifiedLoginUrl)
            val hasEnteredEams = homeUrl.contains("eams") &&
                    ssoResp.status.value in 200..399 &&
                    !homeUrl.contains("/login")
            if (hasEnteredEams) {
                // 提取教务基地址
                session.eamsBaseUrl = if (homeUrl.contains("/eams")) {
                    homeUrl.substring(0, homeUrl.indexOf("/eams") + 5)
                } else {
                    vpnEamsBase
                }
                Result.success(session)
            } else {
                session.close()
                Result.failure(Exception("教务系统单点登录失败，请稍后重试"))
            }
        } catch (e: Exception) {
            session.close()
            Result.failure(Exception("接入教务系统网络异常: ${e.message}", e))
        }
    }

    // 执行统一身份认证登录流程
    suspend fun login(
        studentId: String,
        passwordRaw: String,
        existingSession: CqustWebVpnSession? = null
    ): CasAuthResult {
        val session = existingSession ?: CqustWebVpnSession()
        var keepSession=false
        return try {
            // 1. 访问网关登录入口，获取网关初始 Cookie 并跟随重定向至 CAS 登录页
            val (actualUrl, loginPageResp) = session.getFollowingRedirects("$GATEWAY/login")
            if (loginPageResp.status.value == 403) {
                return CasAuthResult(
                    success = false,
                    errorMessage = "统一身份认证被网关拦截(403)，请切换为移动数据或家庭宽带网络",
                    statusCode = 403,
                    isNetworkError = true
                )
            }
            if (loginPageResp.status != HttpStatusCode.OK) {
                return CasAuthResult(
                    success = false,
                    errorMessage = "统一身份认证登录页不可达(HTTP ${loginPageResp.status.value})",
                    statusCode = loginPageResp.status.value,
                    isNetworkError = true
                )
            }

            val html = loginPageResp.bodyAsText()
            val salt = Regex("""id=["']pwdEncryptSalt["']\s+value=["']([^"']*)["']""").find(html)?.groupValues?.get(1)
                ?: extractInputValue(html, "pwdEncryptSalt")

            if (salt.isNullOrBlank()) {
                if (session.hasVpnTicket()) {
                    keepSession=true
                    return CasAuthResult(success = true, session = session)
                }
                return CasAuthResult(
                    success = false,
                    errorMessage = "统一身份认证登录页解析失败，学校可能已改版",
                    isNetworkError = true
                )
            }

            val execution = Regex("""id=["']execution["'][^>]*value=["']([^"']*)["']""").find(html)?.groupValues?.get(1)
                ?: extractInputValue(html, "execution") ?: "e1s1"

            // 2. 预检是否需要验证码
            if (checkNeedCaptcha(session, actualUrl, studentId)) {
                return CasAuthResult(
                    success = false,
                    errorMessage = "该账号已被要求输入图形验证码，请稍后重试",
                    isNeedCaptcha = true
                )
            }

            // 3. 密码加密与登录表单提交
            val encryptedPassword = CqustVpnCrypto.encryptCasPassword(passwordRaw, salt)
            val postBody = listOf(
                "username" to studentId,
                "password" to encryptedPassword,
                "lt" to "",
                "execution" to execution,
                "_eventId" to "submit",
                "cllt" to "userNameLogin",
                "dllt" to "generalLogin",
                "captcha" to "",
                "rememberMe" to "true"
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

            val postResp = session.post(
                url = actualUrl,
                body = postBody,
                referer = actualUrl,
                headers = mapOf("Origin" to GATEWAY)
            )

            val redirectLocation = postResp.headers[HttpHeaders.Location]

            // 4. 判定认证结果：若成功，CAS 一定会下发重定向且带有 ticket=ST-
            if (redirectLocation != null && redirectLocation.contains("ticket=ST-")) {
                val startTarget = session.resolveUrl(actualUrl, redirectLocation)
                promoteSession(session, startTarget)
                keepSession=true
                return CasAuthResult(success = true, session = session)
            }

            // 若未返回 ST 票据，说明学号或密码错误
            val failHtml = postResp.bodyAsText()
            val errorMsg = Regex("""id=["']msg["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
                .find(failHtml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
                ?: Regex("""class=["'][^"']*auth_error[^"']*["'][^>]*>([\s\S]*?)</""", RegexOption.IGNORE_CASE)
                    .find(failHtml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()

            val friendlyMsg = when {
                !errorMsg.isNullOrBlank() -> errorMsg
                failHtml.contains("密码错误") || failHtml.contains("账号或密码错误") -> "学号或统一身份认证密码错误"
                else -> "统一身份认证登录失败，请核对学号密码"
            }

            CasAuthResult(
                success = false,
                errorMessage = friendlyMsg,
                statusCode = postResp.status.value,
                isNetworkError = false
            )
        } catch (e: Exception) {
            CasAuthResult(
                success = false,
                errorMessage = "统一身份认证网络异常: ${e.message}",
                isNetworkError = true
            )
        } finally {if(!keepSession&&existingSession==null)session.close()}
    }

    // 跟随 CAS 票据至网关会话提升点
    private suspend fun promoteSession(session: CqustWebVpnSession, startUrl: String) {
        var current = startUrl
        for (hop in 0 until 6) {
            val result = session.get(current)
            if (current.contains("/token-login")) return
            val location = result.headers[HttpHeaders.Location] ?: return
            current = session.resolveUrl(current, location)
        }
    }

    // 预检是否需要验证码
    private suspend fun checkNeedCaptcha(session: CqustWebVpnSession, loginUrl: String, studentId: String): Boolean {
        return try {
            val authBase = loginUrl.substringBefore('?').replace(Regex("/login$"), "")
            val checkUrl = "$authBase/checkNeedCaptcha.htl?username=${URLEncoder.encode(studentId, "UTF-8")}&_=${System.currentTimeMillis()}"
            val resp = session.get(checkUrl, referer = loginUrl, isAjax = true)
            if (resp.status == HttpStatusCode.OK) {
                val text = resp.bodyAsText()
                Regex(""""isNeed"\s*:\s*true""").containsMatchIn(text)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // 提取隐藏表单字段
    private fun extractInputValue(html: String, idOrName: String): String? {
        val pattern1 = Regex("""<input[^>]+id=["']$idOrName["'][^>]+value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val m1 = pattern1.find(html)
        if (m1 != null) return m1.groupValues[1]

        val pattern2 = Regex("""<input[^>]+value=["']([^"']*)["'][^>]+id=["']$idOrName["']""", RegexOption.IGNORE_CASE)
        val m2 = pattern2.find(html)
        if (m2 != null) return m2.groupValues[1]

        val pattern3 = Regex("""<input[^>]+name=["']$idOrName["'][^>]+value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        return pattern3.find(html)?.groupValues?.get(1)
    }
}
