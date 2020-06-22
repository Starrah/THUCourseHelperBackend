package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import com.mongodb.BasicDBObject
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Controller
class AppController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    val appDataCollection by lazy { mongoTemplate.getCollection("appData") }
    val clientLogsCollection by lazy { mongoTemplate.getCollection("clientLogs") }
    val feedbackCollection by lazy { mongoTemplate.getCollection("feedbacks") }


    @RequestMapping("/version_check")
    @ResponseBody
    fun term(): Any {
        val dbObject = appDataCollection.find(BasicDBObject("key", "latest_version")).toList().ifEmpty {
            return ErrMsgEntity("版本更新服务器暂不可用。请与管理员联系。", HttpStatus.SERVICE_UNAVAILABLE)
        }.first()
        return mapOf(
            "versionName" to dbObject["versionName"],
            "url" to dbObject["url"]
        )
    }

    @RequestMapping("/log")
    fun log(@RequestBody message: String): ResponseEntity<*> {
        clientLogsCollection.insertOne(Document().also {
            it["time"] = Date()
            it["message"] = message
        })
        return ResponseEntity.ok("")
    }

    @RequestMapping("/feedback")
    fun feedback(@RequestBody message: String): ResponseEntity<*> {
        feedbackCollection.insertOne(Document().also {
            it["time"] = Date()
            it["message"] = message
        })
        return ResponseEntity.ok("")
    }
}