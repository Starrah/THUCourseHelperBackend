package cn.starrah.thu_course_backend.THUAPI

import cn.starrah.thu_course_backend.utils.CookieJar
import cn.starrah.thu_course_backend.utils.CookiedFuel
import cn.starrah.thu_course_backend.utils.enableCookie
import com.alibaba.fastjson.JSON
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArray
import com.github.kittinunf.fuel.coroutines.awaitString
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import com.mongodb.BasicDBObject
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.*
import org.bson.Document
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.regex.Pattern

data class AccountInfo(val username: String, val password: String)

object THUInfo {

    private lateinit var db: MongoDatabase

    fun initialize(db: MongoDatabase) {
        this.db = db
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val availableAccounts: List<AccountInfo> = try { JSON.parseArray(
        File(this::class.java.classLoader.getResource("/").path + "thu_accounts.json").readText(),
        AccountInfo::class.java
    ) } catch (e: Throwable) { e.printStackTrace(); listOf()}

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

    val CLASSROOM_IFRAME_ACEGI_PATTERN =
        Pattern.compile("(?:src|href)=\"(.*?/j_acegi_login\\.do\\?url=/portal3rd\\.do&amp;m=jasJy_Xs_Js_index&amp;ticket=[a-zA-Z0-9]+)\"")

    val CLASSROOM_IFRAME_HREF_PATTERN =
        Pattern.compile("<a .*? href=\"(.*?/pk\\.classroomctrl\\.do\\?m=qyClassroomState&amp;classroom=(.*?)&amp;weeknumber=(\\d*?))\".*?>(.*?)</a>")

    private val GBKCharset = Charset.forName("GBK")

    var needVPN = false

    private suspend fun checkAndLoginVPN(username: String? = null, password: String? = null) {
        val notInTUNET = Fuel.get("http://info.tsinghua.edu.cn/")
            .awaitStringResponse(GBKCharset).second.url.toString().contains(Regex("[oO]ut"))
        if (!notInTUNET) needVPN = false
        if (!CookiedFuel.get("${INFO_VPN_PREFIX}/").awaitStringResponse().second.url.toString()
                .contains("login")) needVPN = true
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
        needVPN = true
    }

    suspend fun verifyAccountGetZJH(cookieJar: CookieJar): String {
        val personJson = Fuel.get("${INFO_VPN_PREFIX}/getYhlb.jsp").enableCookie(cookieJar)
            .awaitString(GBKCharset).let { JSON.parseObject(it) }
        val zjhString = personJson["ZJH"] as String
        if (!zjhString.matches(Regex("\\d{10}"))) throw Exception("获取的证件号不合法")
        return zjhString
    }

    suspend fun loginAll(username: String, password: String) {
        checkAndLoginVPN(username, password)

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
        ).awaitString()
        val matcher = J_ACEGI_PATTERN.matcher(rootNodeString)
        val acegi_url = if (matcher.find()) matcher.group(1).replace("&amp;", "&")
        else throw throw Exception("登录失败，可能是用户名或密码错误")

        CookiedFuel.get(acegi_url).awaitString(GBKCharset)
    }

    suspend fun loginAll() = getRandomAccount().let { loginAll(it.username, it.password) }

    enum class Task {
        LOGIN,
        CLASSROOM,
        ;
    }

    val classroomInfoCollection by lazy { db.getCollection("classroomInfo") }
    val schoolInfoCollection by lazy { db.getCollection("schoolInfo") }

    suspend fun refreshClassroom() {
        // info-学习
        val rootNodeString = CookiedFuel.get(
            "${if (needVPN) INFO_VPN_PREFIX else INFO_SITE}/tag.54ae34fe4899c309.render.userLayoutRootNode.uP?uP_sparam=focusedTabID&focusedTabID=3&uP_sparam=mode&mode=view&_meta_focusedId=3"
        ).awaitString()
        val matcher = CLASSROOM_IFRAME_ACEGI_PATTERN.matcher(rootNodeString)
        val acegi_url = if (matcher.find()) matcher.group(1).replace("&amp;", "&")
        else throw throw Exception("操作失败，可能是用户名或密码错误")

        // 可查询的教室一览表
        val iframe_str = CookiedFuel.get(acegi_url).awaitString(GBKCharset)
        val matcherHref = CLASSROOM_IFRAME_HREF_PATTERN.matcher(iframe_str)
        val aims = mutableMapOf<String, String>()
        while (matcherHref.find()) {
            aims[matcherHref.group(4)] = matcherHref.group(1).let {
                if (URI(it).host != null) it
                else if (needVPN) "${"https://webvpn.tsinghua.edu.cn"}${it}" else "${ZHJW_SITE}${it}"
            }.replace("&amp;", "&")
        }
        val theCss = CookiedFuel.get("${if (needVPN) ZHJW_VPN_PREFIX else ZHJW_SITE}/styles/zhjw/jashjy.css?vpn-6").awaitString()

        classroomInfoCollection.deleteMany(BasicDBObject())// 把原来的数据全部删除
        for ((aimName, aimUrl) in aims) {
            // 逐个获取新的数据并加入数据库
            val finalHtml = CookiedFuel.get(
                "${if (needVPN) "" else ZHJW_SITE}${aimUrl}".replace(aimName, URLEncoder.encode(aimName, GBKCharset))
            ).awaitString(GBKCharset).run {
                replace("content=\"text/html; charset=gbk\"", "content=\"text/html; charset=utf-8\"").
                replace(Regex("<form.*?</form>", RegexOption.DOT_MATCHES_ALL), "").
                replace(Regex("<link type=\"text/css\" href=\"styles/zhjw/jashjy\\.css.*?\" rel=\"stylesheet\">"),
                    "\n<style>\n${theCss}\n</style>\n")
            }
            classroomInfoCollection.replaceOne(BasicDBObject("name", aimName), Document().apply {
                this["name"] = aimName
                this["html"] = finalHtml
            }, ReplaceOptions().upsert(true))
        }

        // 拿取有固定来源的公告类信息
        val schoolInfos = schoolInfoCollection.find().toList()
        for (schoolInfo in schoolInfos) {
            val path = (if (needVPN) schoolInfo["originVPN"] else schoolInfo["origin"]) as String
            val bytes = CookiedFuel.get(path).awaitByteArray()
            schoolInfoCollection.updateOne(BasicDBObject("_id", schoolInfo["_id"]!!), BasicDBObject("\$set", BasicDBObject("bytes", bytes)), UpdateOptions().upsert(true))
        }
    }

}
