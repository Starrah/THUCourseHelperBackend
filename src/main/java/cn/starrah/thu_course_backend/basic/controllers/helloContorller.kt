package cn.starrah.thu_course_backend.basic.controllers

import cn.starrah.thu_course_backend.THUAPI.THUInfo
import com.mongodb.client.MongoDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody


data class QWQ(val a: Int, val b:String?)

@Controller
class HelloController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate
    private val db: MongoDatabase by lazy { mongoTemplate.db }

    @RequestMapping("/hello")
    @ResponseBody
    fun testJson(): QWQ {
        val res = db.getCollection("test").find().first()?.let { it["qwq"] as String }
        return QWQ(1000, res)
    }
}