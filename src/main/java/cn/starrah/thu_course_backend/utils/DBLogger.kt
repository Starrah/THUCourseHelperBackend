package cn.starrah.thu_course_backend.utils

import com.mongodb.client.MongoCollection
import org.bson.Document
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class DBLogger (val mongoCollection: MongoCollection<Document>) {
    enum class Level {
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }

    var throwWhenLogFail_Setting: Boolean = false

    fun log(message: String, level: Level, throwWhenLogFail: Boolean? = null) {
        try {
            mongoCollection.insertOne(Document().also {
                it["time"] = Date()
                it["level"] = level.name
                it["msg"] = message
            })
        }
        catch (e: Exception) {
            if (throwWhenLogFail ?: throwWhenLogFail_Setting) throw e else e.printStackTrace()
        }
    }

    fun logException(e: Throwable, level: Level = Level.ERROR, throwWhenLogFail: Boolean = false) =
        log(StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString(), level, throwWhenLogFail)


    fun error(message: String) = log(message, Level.ERROR)

    fun warning(message: String) = log(message, Level.WARNING)

    fun info(message: String) = log(message, Level.INFO)

    fun debug(message: String) = log(message, Level.DEBUG)
}