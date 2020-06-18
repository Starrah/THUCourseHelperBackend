package cn.starrah.thu_course_backend.controllers

import cn.starrah.thu_course_backend.THUAPI.THUInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TimedTask: CoroutineScope by CoroutineScope(Dispatchers.Default), DisposableBean {
    override fun destroy() {
        cancel()
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * 每天0点、上午10点、下午16点各刷新一次（整点延迟3分钟）。
     */
    @Scheduled(cron = "0 3 0,10,16 * * *")
    fun refresh8hLevelDataAsync() = launch {
        logger.info("THUInfo refresh (8hLevel, Classroom) has started")
        try {
            THUInfo.loginAll()
            THUInfo.refreshClassroom()
            logger.info("THUInfo refresh (8hLevel, Classroom) has got SUCCESS")
        }
        catch (e: Exception) {
            logger.error("THUInfo refresh (8hLevel, Classroom) has got FAILED", e)
        }
    }

}