package cn.starrah.thu_course_backend.THUAPI

import cn.starrah.thu_course_backend.utils.CookieJar
import cn.starrah.thu_course_backend.utils.CookiedFuel
import cn.starrah.thu_course_backend.utils.enableCookie
import com.alibaba.fastjson.JSON
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitString
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

data class AccountInfo(val username: String, val password: String)

object THUInfo {
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val availableAccounts: List<AccountInfo> = JSON.parseArray(
        File(this::class.java.classLoader.getResource("/").path + "thu_accounts.json").readText(),
        AccountInfo::class.java
    )

    private fun getRandomAccount(): AccountInfo {
        return availableAccounts.random()
    }

    val WEBVPN_SITE = "https://webvpn.tsinghua.edu.cn"
    val INFO_SITE = "http://info.tsinghua.edu.cn"
    val ZHJW_SITE = "http://zhjw.cic.tsinghua.edu.cn"

    val INFO_VPN_PREFIX = "$WEBVPN_SITE/http/77726476706e69737468656265737421f9f9479369247b59700f81b9991b2631506205de"
    val ZHJW_VPN_PREFIX =
        "$WEBVPN_SITE/http/77726476706e69737468656265737421eaff4b8b69336153301c9aa596522b20bc86e6e559a9b290"

    val J_ACEGI_PATTERN =
        Pattern.compile("(?:src|href)=\"(.*?/j_acegi_login\\.do\\?url=/jxmh\\.do&amp;m=bks_jxrl&amp;ticket=[a-zA-Z0-9]+)\"")

    private val GBKCharset = Charset.forName("GBK")

    private suspend fun checkAndLoginVPN(username: String? = null, password: String? = null): Boolean {
        val needVPN = Fuel.get("http://info.tsinghua.edu.cn/")
            .awaitStringResponse(GBKCharset).second.url.toString().contains(Regex("[oO]ut"))
        if (!needVPN) return false
        if (!CookiedFuel.get("${INFO_VPN_PREFIX}/").awaitStringResponse().second.url.toString()
                .contains("login")) return true
        val VPNAccount = if (username != null && password != null)
            AccountInfo(username, password) else getRandomAccount()
        val respStr = CookiedFuel.post(
            "${WEBVPN_SITE}/do-login?local_login=true", listOf(
                "auth_type" to "local",
                "username" to VPNAccount.username,
                "password" to VPNAccount.password,
                "sms_code" to ""
            )
        ).awaitString(GBKCharset)
        if ("验证码" in respStr) throw Exception("Web VPN要求验证码，请过一段时间再尝试。")
        if ("密码错误" in respStr) throw Exception("Web VPN错误的用户名或密码")
        return true
    }

    suspend fun verifyAccountGetZJH(cookieJar: CookieJar): String {
        val personJson = Fuel.get("${INFO_VPN_PREFIX}/getYhlb.jsp").enableCookie(cookieJar)
            .awaitString(GBKCharset).let { JSON.parseObject(it) }
        return personJson["ZJH"] as String
    }

    suspend fun loginAll(username: String, password: String) {
        val needVPN = checkAndLoginVPN(username, password)

        val (_, resp, _) = CookiedFuel.post(
            "${if (needVPN) INFO_VPN_PREFIX else INFO_SITE}/Login", listOf(
                "redirect" to "NO",
                "userName" to username,
                "password" to password,
                "x" to "34",
                "y" to "4"
            )
        ).awaitStringResponse(GBKCharset)
        if (resp.url.toString().run { substring(length - 1) } != "1")
            throw Exception("登录失败，可能是用户名或密码错误")

        val rootNodeString = CookiedFuel.get(
            "${if (needVPN) INFO_VPN_PREFIX else INFO_SITE}/render.userLayoutRootNode.uP"
        ).awaitString(GBKCharset)
        val matcher = J_ACEGI_PATTERN.matcher(rootNodeString)
        val acegi_url = if (matcher.find()) matcher.group(1).replace("&amp;", "&")
        else throw throw Exception("登录失败，可能是用户名或密码错误")

        CookiedFuel.get(acegi_url).awaitString(GBKCharset)
    }

    enum class Task {
        LOGIN,
        CLASSROOM,
        ;
    }

    fun executeTaskAsync(tasks: List<Task>, callback: ((Result<Any>) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            var exception: Throwable? = null
            try {
                for (task in tasks) {
                    when (task) {
                        Task.LOGIN     -> getRandomAccount().let { loginAll(it.username, it.password) }
                        Task.CLASSROOM -> TODO()
                    }
                }
            }
            catch (e: Throwable) {
                exception = e
            }
            if (callback != null) {
                withContext(Dispatchers.Main) {
                    if (exception == null) {
                        callback(Result.success("success"))
                    }
                    else {
                        callback(Result.failure(exception))
                    }
                }
            }
        }
    }
}