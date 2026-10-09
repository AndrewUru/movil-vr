package com.muralar.app.ar

import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/** Immutable snapshot crossing from the GL thread to the UI; never exposes an ARCore object. */
data class ArStatus(
    val camera: TrackingState,
    val failure: TrackingFailureReason,
    val walls: Int,
    val anchor: TrackingState?,
    val distanceMeters: Float? = null,
)
