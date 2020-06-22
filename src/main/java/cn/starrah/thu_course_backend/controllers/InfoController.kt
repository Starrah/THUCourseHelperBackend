package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import cn.starrah.thu_course_backend.utils.getPrefix
import com.mongodb.BasicDBObject
import org.bson.types.Binary
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
import java.net.URI
import javax.servlet.http.HttpServletRequest

@Controller
class InfoController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    val classroomInfoCollection by lazy { mongoTemplate.getCollection("classroomInfo") }
    val schoolInfoCollection by lazy { mongoTemplate.getCollection("schoolInfo") }

    @RequestMapping("/XKTime")
    fun XKTime(): ResponseEntity<*> {
        val dbObject = schoolInfoCollection.find(BasicDBObject("key", "XKTime")).toList().ifEmpty {
            return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val bytes = (dbObject["bytes"] as Binary).data ?: return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        return ResponseEntity(bytes, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }

    @RequestMapping("/allClassroom")
    @ResponseBody
    fun allClassroom(req: HttpServletRequest): Any {
        val names = classroomInfoCollection.find().projection(BasicDBObject("name", 1)).toList().map { it["name"] as String }
        return names.map { mapOf("name" to it, "url" to "${getPrefix(req)}/classroom?name=${it}") }
    }

    @RequestMapping("/classroom")
    @ResponseBody
    fun classroom(@RequestParam name: String): ResponseEntity<*> {
        val dbObject = classroomInfoCollection.find(BasicDBObject("name", name)).toList().ifEmpty {
            return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val html = dbObject["html"] as String? ?: return ErrMsgEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        return ResponseEntity(html, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }

    @RequestMapping("/infoList")
    @ResponseBody
    fun infoList(req: HttpServletRequest): Any {
        val result = mutableListOf<Map<String, Any>>()

        val fixedInfos = schoolInfoCollection.find().toList().map {
            Pair(it["name"] as String, "${getPrefix(req)}${it["url"] as String}")
        }
        result += fixedInfos.map { mapOf(
            "name" to it.first,
            "url" to it.second
        ) }

        val names = classroomInfoCollection.find().projection(BasicDBObject("name", 1)).toList().map { it["name"] as String }
        val classRoomObj = mapOf(
            "name" to "教室情况",
            "children" to names.map { mapOf(
                "name" to it,
                "url" to "${getPrefix(req)}/classroom?name=${it}"
            ) }
        )
        result += classRoomObj

        return result
    }
}