package me.eroi.lolidaily.muzei.util

import java.security.MessageDigest

object Md5 {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
