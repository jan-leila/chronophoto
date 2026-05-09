package com.chronophoto.data.capture

data class LiveCameraReadout(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focalLengthMm: Float? = null,
    val apertureFNumber: Float? = null,
    val whiteBalanceMode: String? = null,
    val focusDistanceDiopters: Float? = null,
)
