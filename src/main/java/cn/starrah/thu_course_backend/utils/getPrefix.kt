package cn.starrah.thu_course_backend.utils

import java.net.URI
import javax.servlet.http.HttpServletRequest

fun getPrefix(req: HttpServletRequest, myPath: String? = null): String {
    val uri = URI(req.requestURL.toString())
    val host = uri.host
    val port = if (uri.port != -1) uri.port else null
    val (scheme, showPort) = when(port) {
        443 -> Pair("https", null)
        80 -> Pair("http", null)
        else -> Pair(uri.scheme?:"http", port)
    }
    val path = uri.path?.let {
        if (myPath == null) runCatching { it.substring(0, it.lastIndexOf("/")) }.getOrElse { "" }
        else it.replace(myPath, "")
    }
    return URI(scheme, null, host, showPort?:-1, path, null, null).toString()
}