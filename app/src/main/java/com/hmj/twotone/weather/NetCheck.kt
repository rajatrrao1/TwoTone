package com.hmj.twotone.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Finds out why the weather can't connect, in three steps:
 *  1. does the phone have a network, and has Android confirmed it reaches the internet?
 *  2. can this app look up the weather server's name?
 *  3. can this app actually talk to the weather server?
 * Must be called off the main thread.
 */
object NetCheck {
    class Result(val lines: List<String>, val advice: String)

    fun run(context: Context): Result {
        val lines = mutableListOf<String>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 1. The phone's own connection
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val kind = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wi-fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        @Suppress("DEPRECATION")
        val blocked = cm.activeNetworkInfo?.detailedState == android.net.NetworkInfo.DetailedState.BLOCKED
        lines += if (kind == null) "network: none" else "network: $kind" + if (validated) ", working" else ", not confirmed working"
        if (blocked) lines += "android reports this app as blocked"

        // 2. Name lookup
        val dnsOk = try { InetAddress.getByName("api.open-meteo.com"); true } catch (e: Exception) { false }
        lines += "name lookup: " + if (dnsOk) "ok" else "failed"

        // 3. Talking to the server
        val httpOk = dnsOk && try {
            val c = URL("https://api.open-meteo.com/v1/forecast?latitude=52.37&longitude=4.89&current=temperature_2m")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val code = c.responseCode
            c.disconnect()
            lines += "weather server: answered $code"
            code in 200..299
        } catch (e: Exception) {
            lines += "weather server: ${e.javaClass.simpleName}"
            false
        }

        val advice = when {
            httpOk -> "Everything works now. Go back and tap refresh."
            kind == null -> "The phone has no network at all. Turn on Wi-Fi or mobile data, and check that Offline+ is off."
            blocked || (validated && !dnsOk) ->
                "The phone is online, but this app is kept off the network. Open Android settings > Apps > Two Tone > " +
                    "Mobile data & Wi-Fi (or Network access) and allow it, then come back."
            !validated -> "The phone is connected but Android can't reach the internet through it. Try another Wi-Fi, or mobile data."
            !dnsOk -> "Names can't be looked up. If Private DNS is set in Android settings > Network, try turning it off."
            else -> "The connection works but the weather server didn't answer properly. Try again in a while."
        }
        return Result(lines, advice)
    }
}
