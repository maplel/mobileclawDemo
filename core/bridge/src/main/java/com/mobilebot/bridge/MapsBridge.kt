package com.mobilebot.bridge

interface MapsBridge {
    /**
     * @param mode "search" opens place search; "navigate" opens directions-style flow when supported.
     */
    fun openMap(
        query: String,
        mode: String,
    ): Boolean
}
