package com.example.appcrafters

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform