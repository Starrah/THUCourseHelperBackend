package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.THUAPI.THUInfo
import cn.starrah.thu_course_backend.utils.CookieJar
import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.mongodb.BasicDBObject
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.types.Binary
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import javax.servlet.http.HttpServletRequest

@Controller
class UserDataController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    val userDataCollection by lazy { mongoTemplate.getCollection("userData") }

    data class UserDataAPIBody(
        val authentication: CookieJar,
        val termId: String,
        val calendarData: String? = null,
        val preference: String? = null
    )

    @RequestMapping("/uploadUserData")
    fun uploadUserData(@RequestBody body: UserDataAPIBody): ResponseEntity<*> {
        if (!(body.calendarData != null)) return ErrMsgEntity("上传数据不能为空！", HttpStatus.BAD_REQUEST)
        val zjh = try {
            runBlocking {
                THUInfo.verifyAccountGetZJH(body.authentication)
            }
        }
        catch (e: Exception) {
            return ErrMsgEntity("身份验证失败。请您重新登录后再尝试。", HttpStatus.UNAUTHORIZED)
        }

        val toSaveDoc = Document().also {
            it["calendarData"] = body.calendarData
            it["preference"] = body.preference
        }
        userDataCollection.updateOne(BasicDBObject("zjh", zjh), BasicDBObject("\$set", BasicDBObject(body.termId, toSaveDoc)), UpdateOptions().upsert(true))

        return ResponseEntity(JSON.toJSONString(mapOf("zjh" to zjh)), HttpStatus.OK)
    }

    @RequestMapping("/downloadUserData")
    @ResponseBody
    fun downloadUserData(@RequestBody body: UserDataAPIBody): Any {
        val zjh = try {
            runBlocking {
                THUInfo.verifyAccountGetZJH(body.authentication)
            }
        }
        catch (e: Exception) {
            return ErrMsgEntity("身份验证失败。请您重新登录后再尝试。", HttpStatus.UNAUTHORIZED)
        }

        val doc = userDataCollection.find(BasicDBObject("zjh", zjh)).toList().ifEmpty {
            return ErrMsgEntity("您之前没有保存过数据，因而无法恢复数据。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first().also {
            if (it[body.termId] == null || (it[body.termId] as Document)["calendarData"] == null)
                return ErrMsgEntity("您之前没有保存过数据，因而无法恢复数据。", HttpStatus.SERVICE_UNAVAILABLE)
        }[body.termId] as Document

        val res = mapOf(
            "zjh" to zjh,
            "calendarData" to JSON.parse(doc["calendarData"] as String),
            "preference" to JSON.parse(doc["preference"] as String)
        )
        return res
    }
}