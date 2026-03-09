package com.localmind.app.core.utils

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PocketPal-style GPU capability checker.
 *
 * PocketPal (deviceCapabilities.ts) teen conditions check karta hai Android pe:
 *   1. Adreno GPU hona chahiye (EGL renderer string se detect)
 *   2. CPU i8mm feature hona chahiye (/proc/cpuinfo)
 *   3. CPU dotprod / asimddp feature hona chahiye (/proc/cpuinfo)
 *
 * Teeno TRUE ho tabhi GPU enable hoti hai. Ek bhi false = CPU-only mode.
 * Ye ensure karta hai ki non-Qualcomm devices (jaise V2135 phones) pe
 * GPU blindly enable na ho — jo crash aur incorrect results deta tha.
 */
data class GpuCapability(
    val isAdrenoGpu: Boolean,
    val hasI8mm: Boolean,
    val hasDotprod: Boolean,
    val gpuRendererString: String,
    val isSupported: Boolean = isAdrenoGpu && hasI8mm && hasDotprod
) {
    fun toLogString(): String =
        "GPU=$gpuRendererString | Adreno=$isAdrenoGpu | i8mm=$hasI8mm | dotprod=$hasDotprod | supported=$isSupported"
}

@Singleton
class GpuCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GpuCapabilityChecker"
    }

    /**
     * PocketPal-exact GPU support check.
     * EGL context create karo, GL_RENDERER string padho, /proc/cpuinfo check karo.
     */
    fun checkGpuSupport(): GpuCapability {
        val rendererString = getGlRendererString()
        val isAdreno = isAdrenoGpu(rendererString)
        val cpuFeatures = readCpuFeatures()
        val hasI8mm = cpuFeatures.contains("i8mm")
        val hasDotprod = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")

        val capability = GpuCapability(
            isAdrenoGpu = isAdreno,
            hasI8mm = hasI8mm,
            hasDotprod = hasDotprod,
            gpuRendererString = rendererString
        )

        Log.i(TAG, "GPU capability check: ${capability.toLogString()}")
        return capability
    }

    /**
     * EGL context create karke OpenGL GL_RENDERER string padho.
     * PocketPal ka HardwareInfoModule.kt exactly yahi karta hai.
     */
    private fun getGlRendererString(): String {
        return try {
            // EGL display initialize karo
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "EGL: No display available")
                return "unknown"
            }

            val versionMajor = IntArray(1)
            val versionMinor = IntArray(1)
            if (!EGL14.eglInitialize(display, versionMajor, 0, versionMinor, 0)) {
                Log.w(TAG, "EGL: Initialize failed")
                return "unknown"
            }

            // Config choose karo
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
                || numConfigs[0] == 0) {
                Log.w(TAG, "EGL: No configs available")
                EGL14.eglTerminate(display)
                return "unknown"
            }

            // Context create karo
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "EGL: Context creation failed")
                EGL14.eglTerminate(display)
                return "unknown"
            }

            // 1x1 pbuffer surface banao
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0]!!, surfaceAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)

            // GL_RENDERER padho
            val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: "unknown"

            // Cleanup
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            Log.i(TAG, "GL_RENDERER = $renderer")
            renderer
        } catch (e: Exception) {
            Log.w(TAG, "EGL renderer detection failed", e)
            "unknown"
        }
    }

    /**
     * Renderer string se Adreno GPU detect karo.
     * PocketPal: 'adreno', 'qcom', 'qualcomm' patterns check karta hai.
     */
    private fun isAdrenoGpu(renderer: String): Boolean {
        val lower = renderer.lowercase()
        return lower.contains("adreno") || lower.contains("qcom") || lower.contains("qualcomm")
    }

    /**
     * /proc/cpuinfo se CPU features padho.
     * PocketPal: i8mm aur dotprod/asimddp check karta hai.
     */
    private fun readCpuFeatures(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            // "Features" line dhundo
            val featuresLine = cpuInfo.lines()
                .firstOrNull { it.startsWith("Features", ignoreCase = true) }
                ?: ""
            featuresLine.lowercase()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read /proc/cpuinfo", e)
            ""
        }
    }
}
