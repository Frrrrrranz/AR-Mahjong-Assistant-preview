package com.example.ai_assist.model

import com.google.gson.annotations.SerializedName

/**
 * 服务端 /api/detect-tiles 的单个检测框数据
 * 包含牌名、边界框坐标和置信度
 */
data class TileDetection(
    @SerializedName("class_name")
    val className: String = "",

    @SerializedName("x1")
    val x1: Float = 0f,

    @SerializedName("y1")
    val y1: Float = 0f,

    @SerializedName("x2")
    val x2: Float = 0f,

    @SerializedName("y2")
    val y2: Float = 0f,

    @SerializedName("confidence")
    val confidence: Float = 0f
)

/**
 * 服务端 /api/detect-tiles 轻量检测 API 的完整响应
 * 用于实时检测模式的连续拍照检测
 */
data class DetectTilesResponse(
    @SerializedName("detections")
    val detections: List<TileDetection> = emptyList(),

    @SerializedName("inference_time_ms")
    val inferenceTimeMs: Float = 0f
)
