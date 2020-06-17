package cn.starrah.thu_course_backend.basic.controllers

import cn.starrah.thu_course_backend.utils.ErrMsgEntity
import com.mongodb.BasicDBObject
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Controller
class TermController {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    data class TermAPIResp(val termData: Any, val termList: List<TermDescription>?, val currentTermId: String?){
        data class TermDescription(val id: String, val name: String)
    }

    val termsCollection by lazy { mongoTemplate.getCollection("terms") }
    val timeRulesCollection by lazy { mongoTemplate.getCollection("timeRules") }

    @RequestMapping("/term")
    @ResponseBody
    fun term(id: String?): Any {
        if (id != null) {
            val splitedId = id.split(",")
            val termQuery = termsCollection.find(BasicDBObject(mapOf(
                "schoolName" to splitedId[0],
                "beginYear" to splitedId[1].toInt(),
                "type" to splitedId[2]
            ))).projection(BasicDBObject("_id", 0)).toList()
            if (termQuery.isEmpty()) return ErrMsgEntity("所选择的学期数据在后端系统中不存在。这是一个系统错误，请联系管理员。", HttpStatus.BAD_REQUEST)
            return TermAPIResp(termQuery[0].also { term ->
                if ("timeRule" !in term) term["timeRule"] = timeRulesCollection.find(BasicDBObject("schoolName", term["schoolName"]!!))
                .projection(BasicDBObject("_id", 0)).toList().let { if (it.isNotEmpty()) it[0]["timeRule"] else null }
            }, null, null)
        }
        else {
            val timeMap = timeRulesCollection.find().projection(BasicDBObject("_id", 0))
                .associate { Pair(it["schoolName"] as String, it["timeRule"]) }
            val termList = termsCollection.find().projection(BasicDBObject("_id", 0))
                .toMutableList().onEach { if ("timeRule" !in it) it["timeRule"] = timeMap[it["schoolName"] as String] }
            termList.sortBy {
                val startDate = LocalDate.parse(it["startDate"] as String, DateTimeFormatter.ISO_DATE)
                val termDay = (it["normalWeekCount"] as Int + it["examWeekCount"] as Int) * 7
                val endDate = startDate + Period.ofDays(termDay)
                val now = LocalDate.now()
                val dayBeforeEnd = Period.between(now, endDate).get(ChronoUnit.DAYS).toInt()
                val sortValue = if (now > endDate) 100000 - dayBeforeEnd // 对于已经彻底结束的学期，排序值为一个很大的值以确保排在后面
                else if (now in startDate..endDate) dayBeforeEnd - 100000 // 对于正在进行中的学期，排序值为一个很小的值以确保排在前面
                else dayBeforeEnd
                sortValue
            }
            if (termList.isEmpty()) return ErrMsgEntity("所选择的学期数据在后端系统中不存在。这是一个系统错误，请联系管理员。", HttpStatus.BAD_REQUEST)
            fun makeDescription(termDoc: Document): TermAPIResp.TermDescription {
                val termId = "${termDoc["schoolName"]},${termDoc["beginYear"]},${termDoc["type"]}"
                val year2bit = (termDoc["beginYear"] as Int) % 100
                val seasonName = when (termDoc["type"] as String) {
                    "SPRING" -> "春"
                    "SUMMER" -> "夏"
                    "AUTUMN" -> "秋"
                    "WINTER" -> "冬"
                    else -> ""
                }
                val name = "${year2bit}-${year2bit+1}${seasonName}"
                return TermAPIResp.TermDescription(termId, name)
            }
            return TermAPIResp(termList[0], termList.map { makeDescription(it) }, makeDescription(termList[0]).id)
        }
    }
}