package cn.xfangfang.biliduo.ui.login

import android.content.Context
import cn.xfangfang.biliduo.data.api.BilibiliApi

/**
 * Persists the session cookies returned by passport login.
 * Returns false when the response did not carry a complete session.
 */
internal fun saveLoginSession(context: Context, cookies: Map<String, String>): Boolean {
    val sessdata = cookies["SESSDATA"]
    val biliJct = cookies["bili_jct"]
    val dedeUserId = cookies["DedeUserID"]
    if (sessdata.isNullOrEmpty() || biliJct.isNullOrEmpty() || dedeUserId.isNullOrEmpty()) {
        return false
    }
    BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
    context.getSharedPreferences("bili_prefs", 0).edit()
        .putString("sessdata", sessdata)
        .putString("bili_jct", biliJct)
        .putString("dede_userid", dedeUserId)
        .apply()
    return true
}
