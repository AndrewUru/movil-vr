package com.muralar.app.render

import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Camera texture and world-space geometry. No screen-space placement fallback. */
class GlScene {
    var cameraTexture = 0
        private set
    private var cameraProgram = 0
    private var geometryProgram = 0
    private var cameraPosition = 0
    private var cameraUv = 0
    private var cameraSampler = 0
    private var position = 0
    private var matrix = 0
    private var color = 0
    private val screen = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv = buffer(FloatArray(8))
    private var planeBuffer = buffer(FloatArray(3 * 128))
    private var outline = buffer(PatternGeometry.outline(0f))
    private var grid = buffer(PatternGeometry.grid(0f))
    private var reference = buffer(PatternGeometry.reference(0f))
    private val wallFill = floatArrayOf(.40f, .94f, .70f, .12f)
    private val wallEdge = floatArrayOf(.60f, 1f, .80f, .7f)
    private val patternFill = floatArrayOf(.15f, .65f, .45f, .10f)
    private val patternGrid = floatArrayOf(.70f, 1f, .85f, .65f)
    private val patternEdge = floatArrayOf(.85f, 1f, .92f, 1f)
    private val referenceColor = floatArrayOf(1f, .55f, .20f, 1f)

    fun create() {
        cameraProgram = program(
            "attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vUv = aUv; }",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uCamera; varying vec2 vUv; void main() { gl_FragColor = texture2D(uCamera, vUv); }",
        )
        geometryProgram = program(
            "uniform mat4 uMvp; attribute vec3 aPosition; void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }",
            "precision mediump float; uniform vec4 uColor; void main() { gl_FragColor = uColor; }",
        )
        cameraPosition = glGetAttribLocation(cameraProgram, "aPosition")
        cameraUv = glGetAttribLocation(cameraProgram, "aUv")
        cameraSampler = glGetUniformLocation(cameraProgram, "uCamera")
        position = glGetAttribLocation(geometryProgram, "aPosition")
        matrix = glGetUniformLocation(geometryProgram, "uMvp")
        color = glGetUniformLocation(geometryProgram, "uColor")
        val textures = IntArray(1)
        glGenTextures(1, textures, 0)
        cameraTexture = textures[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glClearColor(.035f, .055f, .045f, 1f)
        glDisable(GL_CULL_FACE)
    }

    fun drawCamera(frame: Frame) {
        if (frame.timestamp == 0L) return
        screen.rewind()
        uv.rewind()
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            screen, Coordinates2d.TEXTURE_NORMALIZED, uv)
        screen.rewind()
        uv.rewind()
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glDisable(GL_BLEND)
        glUseProgram(cameraProgram)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        glUniform1i(cameraSampler, 0)
        glEnableVertexAttribArray(cameraPosition)
        glEnableVertexAttribArray(cameraUv)
        glVertexAttribPointer(cameraPosition, 2, GL_FLOAT, false, 0, screen)
        glVertexAttribPointer(cameraUv, 2, GL_FLOAT, false, 0, uv)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(cameraPosition)
        glDisableVertexAttribArray(cameraUv)
        glDepthMask(true)
    }

    fun drawPlane(plane: Plane, mvp: FloatArray) {
        val polygon = plane.polygon
        val count = polygon.remaining() / 2
        if (count < 3) return
        if (planeBuffer.capacity() < count * 3) planeBuffer = buffer(FloatArray(count * 3))
        planeBuffer.clear()
        repeat(count) { planeBuffer.put(polygon.get()).put(0.001f).put(polygon.get()) }
        planeBuffer.flip()
        draw(planeBuffer, GL_TRIANGLE_FAN, count, mvp, wallFill)
        draw(planeBuffer, GL_LINE_LOOP, count, mvp, wallEdge)
    }

    fun orientPattern(angle: Float) {
        outline = buffer(PatternGeometry.outline(angle))
        grid = buffer(PatternGeometry.grid(angle))
        reference = buffer(PatternGeometry.reference(angle))
    }

    fun drawPattern(mvp: FloatArray) {
        draw(outline, GL_TRIANGLE_FAN, 4, mvp, patternFill)
        draw(grid, GL_LINES, grid.limit() / 3, mvp, patternGrid)
        draw(outline, GL_LINE_LOOP, 4, mvp, patternEdge)
        draw(reference, GL_TRIANGLES, 3, mvp, referenceColor)
    }

    private fun draw(vertices: FloatBuffer, mode: Int, count: Int, mvp: FloatArray, rgba: FloatArray) {
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        // Transparent surfaces must not occlude one another using a filled depth rectangle.
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glUseProgram(geometryProgram)
        glUniformMatrix4fv(matrix, 1, false, mvp, 0)
        glUniform4fv(color, 1, rgba, 0)
        vertices.rewind()
        glEnableVertexAttribArray(position)
        glVertexAttribPointer(position, 3, GL_FLOAT, false, 0, vertices)
        glLineWidth(2f)
        glDrawArrays(mode, 0, count)
        glDisableVertexAttribArray(position)
        glDepthMask(true)
    }

    private fun program(vertex: String, fragment: String): Int {
        val v = shader(GL_VERTEX_SHADER, vertex)
        val f = shader(GL_FRAGMENT_SHADER, fragment)
        val result = glCreateProgram()
        glAttachShader(result, v)
        glAttachShader(result, f)
        glLinkProgram(result)
        val success = IntArray(1)
        glGetProgramiv(result, GL_LINK_STATUS, success, 0)
        check(success[0] == GL_TRUE) { glGetProgramInfoLog(result) }
        glDeleteShader(v)
        glDeleteShader(f)
        return result
    }

    private fun shader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        val success = IntArray(1)
        glGetShaderiv(shader, GL_COMPILE_STATUS, success, 0)
        check(success[0] == GL_TRUE) { glGetShaderInfoLog(shader) }
        return shader
    }

    private fun buffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); rewind() }
}
