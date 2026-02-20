package com.example.ai_assist

import android.app.Application

// NOTE: 已移除 RayNeo MercurySDK 初始化（手机版不需要 AR SDK）
class MercuryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}