package com.muralar.app.ar

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent

/** A real click action is also available to accessibility services (at the viewport center). */
class ArSurfaceView(context: Context) : GLSurfaceView(context) {
    var onArTap: ((Float, Float) -> Unit)? = null
    private var touchPoint: Pair<Float, Float>? = null
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            touchPoint = e.x to e.y
            return true
        }
    })

    init { isClickable = true; isFocusable = true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestures.onTouchEvent(event)
        if (touchPoint != null) performClick()
        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        val point = touchPoint ?: (width / 2f to height / 2f)
        touchPoint = null
        onArTap?.invoke(point.first, point.second)
        return true
    }
}
