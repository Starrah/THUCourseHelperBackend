package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import cn.starrah.thu_course_backend.utils.getPrefix
import com.mongodb.BasicDBObject
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.servlet.http.HttpServletRequest
import kotlin.coroutines.Continuation

@Controller
class InfoController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    val classroomInfoCollection by lazy { mongoTemplate.getCollection("classroomInfo") }
    val schoolInfoCollection by lazy { mongoTemplate.getCollection("schoolInfo") }
    val pullMessageCollection by lazy { mongoTemplate.getCollection("pullMessage") }

    @RequestMapping("/info")
    fun info(@RequestParam name: String): ResponseEntity<*> {
        val dbObject = schoolInfoCollection.find(BasicDBObject("key", name)).toList().ifEmpty {
            return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val bytes = (dbObject["bytes"] as? Binary)?.data ?: return ErrMsgEntity(
            "该功能暂不可用。请与管理员联系。",
            HttpStatus.SERVICE_UNAVAILABLE
        )
        return ResponseEntity(bytes, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }

    @RequestMapping("/allClassroom")
    @ResponseBody
    fun allClassroom(req: HttpServletRequest): Any {
        val names =
            classroomInfoCollection.find().projection(BasicDBObject("name", 1)).toList().map { it["name"] as String }
        return names.map { mapOf("name" to it, "url" to "${getPrefix(req)}/classroom?name=${it}") }
    }

    @RequestMapping("/classroom")
    @ResponseBody
    fun classroom(@RequestParam name: String): ResponseEntity<*> {
        val dbObject = classroomInfoCollection.find(BasicDBObject("name", name)).toList().ifEmpty {
            return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val html =
            dbObject["html"] as String? ?: return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        return ResponseEntity(html, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }

    @RequestMapping("/infoList")
    @ResponseBody
    fun infoList(req: HttpServletRequest): Any {
        val result = mutableListOf<Map<String, Any>>()

        val fixedInfos = schoolInfoCollection.find().toList().map {
            if (it["url"] is String) {
                Pair(it["name"] as String, it["url"] as String)
            }
            else {
                Pair(it["name"] as String, "${getPrefix(req)}/info?name=${it["key"] as String}")
            }
        }
        result += fixedInfos.map {
            mapOf(
                "name" to it.first,
                "url" to it.second
            )
        }

        val names =
            classroomInfoCollection.find().projection(BasicDBObject("name", 1)).toList().map { it["name"] as String }
        val classRoomObj = mapOf(
            "name" to "教室情况",
            "children" to names.map {
                mapOf(
                    "name" to it,
                    "url" to "${getPrefix(req)}/classroom?name=${it}"
                )
            }
        )
        result += classRoomObj

        return result
    }

    @RequestMapping("/pullMessage")
    @ResponseBody
    fun pullMessage(@RequestParam version: String, @RequestParam type: String?): Any {
        data class _PullMessageResp(
            val id: String,
            val title: String,
            val body: String?,
            val time: String?,
            val intentUri: String?
        )

        val messages = pullMessageCollection.find().toList()
        val parsedMessages = messages.mapNotNull {
            val time = (it["time"] as Date?)?.toInstant()?.atZone(ZoneId.systemDefault())
            val allowSendTimeRange = LocalDateTime.now().let { (it - Duration.ofHours(8))..(it + Duration.ofDays(7)) }
            if (time != null && time.toLocalDateTime() !in allowSendTimeRange) null
            else if (it["version"] as String? == version) null
            else _PullMessageResp(
                (it["_id"] as ObjectId).toString().let { it.substring(it.length - 6) },
                it["title"] as String,
                it["body"] as String?,
                time?.format(DateTimeFormatter.ISO_DATE_TIME),
                it["intentUri"] as String?
            )
        }
        return parsedMessages
    }
}