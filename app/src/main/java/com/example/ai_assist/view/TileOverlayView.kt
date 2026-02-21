package com.example.ai_assist.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.ai_assist.model.TileDetection

/**
 * 相机预览叠加视图：在实时检测模式下绘制 YOLO 检测框和牌名标签。
 * 
 * NOTE: 检测框坐标基于原始图像像素坐标，需要通过 setImageSize 设置
 * 原始图像尺寸以便正确缩放到 View 坐标系。
 */
class TileOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<TileDetection> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // 检测框画笔：半透明绿色边框
    private val boxPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // 标签背景画笔
    private val labelBgPaint = Paint().apply {
        color = Color.parseColor("#CC4CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 标签文字画笔
    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }

    // 置信度文字画笔
    private val confTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        textSize = 20f
        isAntiAlias = true
    }

    // 信息栏画笔（显示检测数量和推理时间）
    private val infoBgPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }

    private val infoTextPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        textSize = 24f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private var inferenceTimeMs: Float = 0f

    /**
     * 更新检测结果并触发重绘
     */
    fun updateDetections(newDetections: List<TileDetection>, inferenceMs: Float) {
        detections = newDetections
        inferenceTimeMs = inferenceMs
        invalidate()
    }

    /**
     * 设置原始图像尺寸，用于坐标缩放计算
     */
    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    /**
     * 清空所有检测框
     */
    fun clearDetections() {
        detections = emptyList()
        inferenceTimeMs = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detections.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // NOTE: 计算原始图像坐标到 View 坐标的缩放因子
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight

        // 绘制每个检测框
        for (detection in detections) {
            val rect = RectF(
                detection.x1 * scaleX,
                detection.y1 * scaleY,
                detection.x2 * scaleX,
                detection.y2 * scaleY
            )

            // 绘制检测框
            canvas.drawRect(rect, boxPaint)

            // 绘制标签背景
            val label = detection.className
            val textWidth = labelTextPaint.measureText(label)
            val labelHeight = 36f
            val labelRect = RectF(
                rect.left,
                rect.top - labelHeight - 4f,
                rect.left + textWidth + 16f,
                rect.top
            )
            canvas.drawRoundRect(labelRect, 6f, 6f, labelBgPaint)

            // 绘制牌名文字
            canvas.drawText(
                label,
                rect.left + 8f,
                rect.top - 10f,
                labelTextPaint
            )

            // 绘制置信度（在框右上角）
            val confStr = "${(detection.confidence * 100).toInt()}%"
            val confWidth = confTextPaint.measureText(confStr)
            canvas.drawText(
                confStr,
                rect.right - confWidth - 4f,
                rect.top + 20f,
                confTextPaint
            )
        }

        // 绘制信息栏（左上角显示检测数量和推理时间）
        val infoText = "检测: ${detections.size}张 | ${inferenceTimeMs.toInt()}ms"
        val infoWidth = infoTextPaint.measureText(infoText) + 24f
        val infoBgRect = RectF(8f, 8f, 8f + infoWidth, 44f)
        canvas.drawRoundRect(infoBgRect, 8f, 8f, infoBgPaint)
        canvas.drawText(infoText, 20f, 36f, infoTextPaint)
    }
}
