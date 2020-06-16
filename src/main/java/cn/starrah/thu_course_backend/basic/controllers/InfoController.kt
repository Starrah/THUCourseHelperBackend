package cn.starrah.thu_course_backend.basic.controllers

import cn.starrah.thu_course_backend.THUAPI.THUInfo
import com.alibaba.fastjson.JSONObject
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
            return ResponseEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val bytes = (dbObject["bytes"] as Binary).data ?: return ResponseEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        return ResponseEntity(bytes, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }

    @RequestMapping("/allClassroom")
    @ResponseBody
    fun allClassroom(req: HttpServletRequest): Any {
        val baseUrl = req.requestURL.replace(Regex("/allClassroom"), "")
        val names = classroomInfoCollection.find().projection(BasicDBObject("name", 1)).toList().map { it["name"] as String }
        return names.map { mapOf("name" to it, "url" to "${baseUrl}/classroom?name=${it}") }
    }

    @RequestMapping("/classroom")
    @ResponseBody
    fun classroom(@RequestParam name: String): ResponseEntity<*> {
        val dbObject = classroomInfoCollection.find(BasicDBObject("name", name)).toList().ifEmpty {
            return ResponseEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        val html = dbObject["html"] as String? ?: return ResponseEntity("该功能暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        return ResponseEntity(html, HttpHeaders().apply { contentType = MediaType.TEXT_HTML }, HttpStatus.OK)
    }
}