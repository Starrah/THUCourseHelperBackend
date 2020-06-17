package cn.starrah.thu_course_backend.utils

import com.alibaba.fastjson.JSON
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap

class ErrMsgEntity(val errMsg: String, val headers: MultiValueMap<String, String>?, val status: HttpStatus) :
    ResponseEntity<String>(JSON.toJSONString(mapOf("errMsg" to errMsg)), (headers?: HttpHeaders()).apply {
        set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
    }, status) {
    constructor(errMsg: String, status: HttpStatus): this(errMsg, null, status)
}