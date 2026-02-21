package com.example.ai_assist.model

import com.google.gson.annotations.SerializedName

/**
 * 服务端 /api/process-audio 接口的返回数据结构
 * 包含语音识别文本、事件列表和更新详情
 */
data class ProcessAudioResponse(
    @SerializedName("transcript")
    val transcript: String = "",

    @SerializedName("events")
    val events: List<Map<String, Any>> = emptyList(),

    @SerializedName("updated_visible_tiles_count")
    val updatedVisibleTilesCount: Int = 0,

    @SerializedName("details")
    val details: List<String> = emptyList()
)
