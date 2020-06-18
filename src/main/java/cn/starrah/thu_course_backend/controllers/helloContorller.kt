package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import com.mongodb.client.MongoDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpStatus
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
    fun testJson(): Any {
        return ErrMsgEntity("错误提示", HttpStatus.I_AM_A_TEAPOT)
    }
}