package com.shardclaw.app.bridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.shardclaw.app.camera.CameraCaptureActivity
import com.shardclaw.app.config.ConfigManager
import com.shardclaw.app.util.Analytics
import com.shardclaw.app.util.ServiceState
import com.shardclaw.app.storage.RustCore
import com.shardclaw.app.storage.StorageManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * AndroidBridge - HTTP server for Node.js <-> Kotlin IPC
 *
 * Runs on localhost:8765 and provides Android-native capabilities
 * to the Node.js agent via simple HTTP POST requests.
 */
class AndroidBridge(
    private val context: Context,
    private val authToken: String,
    port: Int = 8765
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "AndroidBridge"
        private const val AUTH_HEADER = "X-Bridge-Token"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val rustCore = RustCore()
    private val storageManager = StorageManager(context)

    // Per-endpoint rate limiting (thread-safe for NanoHTTPD's thread pool)
    private val rateLimiter = ConcurrentHashMap<String, MutableList<Long>>()
    private val rateLimits = mapOf(
        "/sms" to Pair(5, 60_000L),
        "/call" to Pair(3, 60_000L),
        "/camera/capture" to Pair(10, 60_000L),
        "/contacts/search" to Pair(20, 60_000L),
        "/contacts/add" to Pair(10, 60_000L),
        "/location" to Pair(10, 60_000L),
    )

    @Synchronized
    private fun isRateLimited(endpoint: String): Boolean {
        val limit = rateLimits[endpoint] ?: return false
        val now = System.currentTimeMillis()
        val timestamps = rateLimiter.getOrPut(endpoint) { mutableListOf() }
        timestamps.removeAll { now - it > limit.second }
        if (timestamps.size >= limit.first) return true
        timestamps.add(now)
        return false
    }

    init {
        // Initialize Text-to-Speech
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        // Only allow POST requests
        if (method != Method.POST) {
            return jsonResponse(400, mapOf("error" to "Only POST requests allowed"))
        }

        // Verify auth token on every request (per-boot random secret)
        val token = session.headers?.get(AUTH_HEADER.lowercase())
        if (token != authToken) {
            Log.w(TAG, "Unauthorized request to $uri (bad/missing token)")
            return jsonResponse(403, mapOf("error" to "Unauthorized"))
        }

        // Rate limiting for sensitive endpoints
        if (isRateLimited(uri)) {
            return jsonResponse(429, mapOf("error" to "Rate limit exceeded for $uri"))
        }

        // Parse body
        val body = mutableMapOf<String, String>()
        try {
            session.parseBody(body)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing body", e)
        }

        val postData = body["postData"] ?: "{}"
        val params = try {
            JSONObject(postData)
        } catch (e: Exception) {
            JSONObject()
        }

        return try {
            when (uri) {
                "/battery" -> handleBattery()
                "/storage" -> handleStorage()
                "/network" -> handleNetwork()
                "/clipboard/get" -> handleClipboardGet()
                "/clipboard/set" -> handleClipboardSet(params)
                "/contacts/search" -> handleContactsSearch(params)
                "/contacts/add" -> handleContactsAdd(params)
                "/sms" -> handleSms(params)
                "/call" -> handleCall(params)
                "/location" -> handleLocation()
                "/tts" -> handleTts(params)
                "/camera/capture" -> handleCameraCapture(params)
                "/apps/list" -> handleAppsList()
                "/apps/launch" -> handleAppsLaunch(params)
                "/stats/message" -> handleStatsMessage()
                "/stats/tokens" -> handleStatsTokens(params)
                "/solana/authorize" -> handleSolanaAuthorize()
                "/solana/address" -> handleSolanaAddress()
                "/solana/sign" -> handleSolanaSign(params)
                "/solana/sign-only" -> handleSolanaSignOnly(params)
                "/solana/send" -> handleSolanaSend(params)
                "/config/save-owner" -> handleConfigSaveOwner(params)
                "/stats/db-summary" -> proxyToNodeStats()
                "/shardlock/encode" -> handleShardlockEncode(params)
                "/shardlock/heartbeat" -> handleShardlockHeartbeat(params)
                "/shardlock/status" -> handleShardlockStatus()
                "/shardlock/clear" -> handleShardlockClear(params)
                "/shardlock/decode" -> handleShardlockDecode(params)
                "/shardlock/read-meta" -> handleShardlockReadMeta(params)
                "/ping" -> jsonResponse(200, mapOf("status" to "ok", "bridge" to "AndroidBridge"))
                else -> jsonResponse(404, mapOf("error" to "Unknown endpoint: $uri"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling $uri", e)
            jsonResponse(500, mapOf("error" to e.message))
        }
    }

    // ==================== Battery ====================

    private fun handleBattery(): Response {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = if (scale > 0) (level * 100 / scale) else -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        return jsonResponse(200, mapOf(
            "level" to percentage,
            "isCharging" to isCharging,
            "chargeType" to chargeType
        ))
    }

    // ==================== Storage ====================

    private fun handleStorage(): Response {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val availableBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - availableBytes

        return jsonResponse(200, mapOf(
            "total" to totalBytes,
            "available" to availableBytes,
            "used" to usedBytes,
            "totalFormatted" to formatBytes(totalBytes),
            "availableFormatted" to formatBytes(availableBytes),
            "usedFormatted" to formatBytes(usedBytes)
        ))
    }

    // ==================== Network ====================

    private fun handleNetwork(): Response {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isConnected = network != null
        val type = when {
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "none"
        }

        return jsonResponse(200, mapOf(
            "isConnected" to isConnected,
            "type" to type
        ))
    }

    // ==================== Clipboard ====================

    private fun handleClipboardGet(): Response {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString() ?: ""
        } else {
            ""
        }
        return jsonResponse(200, mapOf("content" to text))
    }

    private fun handleClipboardSet(params: JSONObject): Response {
        val content = params.optString("content", "")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("shardclaw", content)
        clipboard.setPrimaryClip(clip)
        return jsonResponse(200, mapOf("success" to true))
    }

    // ==================== Contacts ====================

    private fun handleContactsSearch(params: JSONObject): Response {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return jsonResponse(403, mapOf("error" to "READ_CONTACTS permission not granted"))
        }

        val query = params.optString("query", "")
        val limit = params.optInt("limit", 10)

        val contacts = mutableListOf<Map<String, String?>>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val name = it.getString(0)
                val phone = it.getString(1)
                contacts.add(mapOf("name" to name, "phone" to phone))
                count++
            }
        }

        return jsonResponse(200, mapOf("contacts" to contacts, "count" to contacts.size))
    }

    private fun handleContactsAdd(params: JSONObject): Response {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return jsonResponse(403, mapOf("error" to "WRITE_CONTACTS permission not granted"))
        }

        val name = params.optString("name", "")
        val phone = params.optString("phone", "")

        if (name.isBlank() || phone.isBlank()) {
            return jsonResponse(400, mapOf("error" to "name and phone are required"))
        }

        // Use intent to add contact (safer, doesn't require raw insert)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return jsonResponse(200, mapOf("success" to true, "message" to "Contact add dialog opened"))
    }

    // ==================== SMS ====================

    private fun handleSms(params: JSONObject): Response {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            return jsonResponse(403, mapOf("error" to "SEND_SMS permission not granted"))
        }

        val phone = params.optString("phone", "")
        val message = params.optString("message", "")

        if (phone.isBlank() || message.isBlank()) {
            return jsonResponse(400, mapOf("error" to "phone and message are required"))
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            // Split long messages
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            return jsonResponse(200, mapOf("success" to true, "phone" to phone, "parts" to parts.size))
        } catch (e: Exception) {
            return jsonResponse(500, mapOf("error" to "Failed to send SMS: ${e.message}"))
        }
    }

    // ==================== Phone Call ====================

    private fun handleCall(params: JSONObject): Response {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return jsonResponse(403, mapOf("error" to "CALL_PHONE permission not granted"))
        }

        val phone = params.optString("phone", "")
        if (phone.isBlank()) {
            return jsonResponse(400, mapOf("error" to "phone is required"))
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return jsonResponse(200, mapOf("success" to true, "phone" to phone))
    }

    // ==================== Location ====================

    private fun handleLocation(): Response {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return jsonResponse(403, mapOf("error" to "ACCESS_FINE_LOCATION permission not granted"))
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try to get last known location
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var bestLocation: Location? = null

        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                        bestLocation = location
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception for provider $provider", e)
            }
        }

        return if (bestLocation != null) {
            jsonResponse(200, mapOf(
                "latitude" to bestLocation.latitude,
                "longitude" to bestLocation.longitude,
                "accuracy" to bestLocation.accuracy,
                "altitude" to bestLocation.altitude,
                "provider" to bestLocation.provider,
                "time" to bestLocation.time
            ))
        } else {
            jsonResponse(200, mapOf("error" to "No location available. Enable GPS and try again."))
        }
    }

    // ==================== Text-to-Speech ====================

    private fun handleTts(params: JSONObject): Response {
        if (!ttsReady) {
            return jsonResponse(503, mapOf("error" to "TTS not ready"))
        }

        val text = params.optString("text", "")
        if (text.isBlank()) {
            return jsonResponse(400, mapOf("error" to "text is required"))
        }

        val pitch = params.optDouble("pitch", 1.0).toFloat()
        val speed = params.optDouble("speed", 1.0).toFloat()

        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shardclaw_tts")

        return jsonResponse(200, mapOf("success" to true, "text" to text))
    }

    // ==================== Camera ====================

    private fun handleCameraCapture(params: JSONObject): Response {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return jsonResponse(403, mapOf("error" to "CAMERA permission not granted"))
        }

        val requestId = java.util.UUID.randomUUID().toString()
        val lens = params.optString("lens", "back").lowercase().let {
            if (it == "front") "front" else "back"
        }

        try {
            val intent = Intent(context, CameraCaptureActivity::class.java).apply {
                putExtra("requestId", requestId)
                putExtra("lens", lens)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            return jsonResponse(500, mapOf("error" to "Failed to start camera capture: ${e.message}"))
        }

        val resultFile = java.io.File(java.io.File(context.filesDir, CameraCaptureActivity.RESULTS_DIR), "$requestId.json")
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (resultFile.exists()) {
                val result = JSONObject(resultFile.readText())
                resultFile.delete()
                val error = result.optString("error", "")
                val imagePath = result.optString("path", "")
                val capturedAt = result.optLong("capturedAt", 0L)

                return if (error.isBlank() && imagePath.isNotBlank()) {
                    jsonResponse(200, mapOf(
                        "success" to true,
                        "path" to imagePath,
                        "lens" to result.optString("lens", lens),
                        "capturedAt" to capturedAt
                    ))
                } else {
                    jsonResponse(400, mapOf("error" to error.ifBlank { "Camera capture failed" }))
                }
            }
            Thread.sleep(250)
        }

        return jsonResponse(408, mapOf("error" to "Camera capture timed out"))
    }

    // ==================== Apps ====================

    private fun handleAppsList(): Response {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { mapOf(
                "name" to pm.getApplicationLabel(it).toString(),
                "package" to it.packageName
            )}
            .sortedBy { it["name"]?.lowercase() }

        return jsonResponse(200, mapOf("apps" to apps, "count" to apps.size))
    }

    private fun handleAppsLaunch(params: JSONObject): Response {
        val packageName = params.optString("package", "")
        if (packageName.isBlank()) {
            return jsonResponse(400, mapOf("error" to "package is required"))
        }

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            return jsonResponse(404, mapOf("error" to "App not found: $packageName"))
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return jsonResponse(200, mapOf("success" to true, "package" to packageName))
    }

    // ==================== Stats ====================

    private fun handleStatsMessage(): Response {
        ServiceState.incrementMessages()
        return jsonResponse(200, mapOf("success" to true))
    }

    private fun handleStatsTokens(params: JSONObject): Response {
        val inputTokens = params.optLong("input_tokens", 0)
        val outputTokens = params.optLong("output_tokens", 0)
        val total = inputTokens + outputTokens
        if (total > 0) {
            ServiceState.addTokens(total)
        }
        val model = params.optString("model", "unknown")
        Analytics.messageSent(model, total)
        return jsonResponse(200, mapOf("success" to true, "tokens_added" to total))
    }

    // ==================== Solana ====================

    private fun handleSolanaAuthorize(): Response {
        val requestId = java.util.UUID.randomUUID().toString()
        val intent = Intent(context, com.shardclaw.app.solana.SolanaAuthActivity::class.java).apply {
            putExtra("action", "authorize")
            putExtra("requestId", requestId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val resultFile = java.io.File(java.io.File(context.filesDir, "solana_results"), "$requestId.json")
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (resultFile.exists()) {
                val result = JSONObject(resultFile.readText())
                resultFile.delete()
                val address = result.optString("address", "")
                val error = result.optString("error", "")

                return if (address.isNotBlank()) {
                    jsonResponse(200, mapOf("address" to address, "success" to true))
                } else {
                    jsonResponse(400, mapOf("error" to error.ifBlank { "Authorization failed" }))
                }
            }
            Thread.sleep(300)
        }
        return jsonResponse(408, mapOf("error" to "Authorization timed out"))
    }

    private fun handleSolanaAddress(): Response {
        val address = ConfigManager.getWalletAddress(context)
        return if (address != null) {
            val label = ConfigManager.getWalletLabel(context)
            jsonResponse(200, mapOf("address" to address, "label" to label))
        } else {
            jsonResponse(404, mapOf("error" to "No wallet connected"))
        }
    }

    private fun handleSolanaSign(params: JSONObject): Response {
        val txBase64 = params.optString("transaction", "")
        if (txBase64.isBlank()) {
            return jsonResponse(400, mapOf("error" to "transaction (base64) is required"))
        }

        val txBytes = android.util.Base64.decode(txBase64, android.util.Base64.NO_WRAP)
        val requestId = java.util.UUID.randomUUID().toString()

        val intent = Intent(context, com.shardclaw.app.solana.SolanaAuthActivity::class.java).apply {
            putExtra("action", "sign")
            putExtra("requestId", requestId)
            putExtra("transaction", txBytes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val resultFile = java.io.File(java.io.File(context.filesDir, "solana_results"), "$requestId.json")
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            if (resultFile.exists()) {
                val result = JSONObject(resultFile.readText())
                resultFile.delete()
                val sigB64 = result.optString("signature", "")
                val error = result.optString("error", "")

                return if (sigB64.isNotBlank()) {
                    jsonResponse(200, mapOf("signature" to sigB64, "success" to true))
                } else {
                    jsonResponse(400, mapOf("error" to error.ifBlank { "Transaction rejected by user" }))
                }
            }
            Thread.sleep(300)
        }
        return jsonResponse(408, mapOf("error" to "Signing timed out"))
    }

    /**
     * Sign-only endpoint for Jupiter Ultra flow.
     * Returns the full signed transaction (base64) without broadcasting.
     * Jupiter Ultra handles broadcasting via /execute.
     */
    private fun handleSolanaSignOnly(params: JSONObject): Response {
        val txBase64 = params.optString("transaction", "")
        if (txBase64.isBlank()) {
            return jsonResponse(400, mapOf("error" to "transaction (base64) is required"))
        }

        val txBytes = try {
            android.util.Base64.decode(txBase64, android.util.Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return jsonResponse(400, mapOf("error" to "transaction is invalid base64"))
        }
        val requestId = java.util.UUID.randomUUID().toString()

        val intent = Intent(context, com.shardclaw.app.solana.SolanaAuthActivity::class.java).apply {
            putExtra("action", "signOnly")
            putExtra("requestId", requestId)
            putExtra("transaction", txBytes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val resultsDir = java.io.File(context.filesDir, com.shardclaw.app.solana.SolanaAuthActivity.RESULTS_DIR)
        val resultFile = java.io.File(resultsDir, "$requestId.json")
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            if (resultFile.exists()) {
                val result = JSONObject(resultFile.readText())
                resultFile.delete()
                val signedTx = result.optString("signedTransaction", "")
                val error = result.optString("error", "")

                return if (signedTx.isNotBlank()) {
                    jsonResponse(200, mapOf("signedTransaction" to signedTx, "success" to true))
                } else {
                    jsonResponse(400, mapOf("error" to error.ifBlank { "Transaction rejected by user" }))
                }
            }
            Thread.sleep(300)
        }
        return jsonResponse(408, mapOf("error" to "Signing timed out"))
    }

    private fun handleSolanaSend(params: JSONObject): Response {
        // Legacy endpoint — solana_send now builds tx in JS and uses /solana/sign
        return jsonResponse(400, mapOf("error" to "Use /solana/sign instead. Transaction building is handled by the Node.js agent."))
    }

    // ==================== Config ====================

    private fun handleConfigSaveOwner(params: JSONObject): Response {
        val ownerId = params.optString("ownerId", "")
        if (ownerId.isBlank()) {
            return jsonResponse(400, mapOf("error" to "ownerId is required"))
        }
        val persisted = ConfigManager.saveOwnerId(context, ownerId)
        return if (persisted) {
            jsonResponse(200, mapOf("success" to true))
        } else {
            jsonResponse(500, mapOf("error" to "Failed to persist owner ID"))
        }
    }

    // ==================== Helpers ====================

    // ==================== Shard-Lock ====================

    private fun handleShardlockEncode(params: JSONObject): Response {
        if (!RustCore.isAvailable()) {
            return jsonResponse(503, mapOf("error" to "Rust-Core not available (librust_core.so not loaded)"))
        }

        val data = params.optString("data", "")
        if (data.isBlank()) {
            return jsonResponse(400, mapOf("error" to "data is required"))
        }

        val encoding = params.optString("encoding", "utf8")
        val dataShards = params.optInt("dataShards", 4)
        val parityShards = params.optInt("parityShards", 2)

        return try {
            val dataBytes = if (encoding == "base64") {
                android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
            } else {
                data.toByteArray()
            }
            val shards = rustCore.encode(dataBytes, dataShards, parityShards)
            val merkleRoot = rustCore.computeMerkleRoot(shards)
            val merkleRootHex = merkleRoot.joinToString("") { "%02x".format(it) }

            // Save shards + metadata to local storage
            storageManager.saveShards(merkleRootHex, shards, dataShards, parityShards, dataBytes.size)

            val totalBytes = shards.sumOf { it.size.toLong() }
            jsonResponse(200, mapOf(
                "merkleRoot" to merkleRootHex,
                "shardCount" to shards.size,
                "totalShards" to shards.size,
                "dataShards" to dataShards,
                "parityShards" to parityShards,
                "bytesStored" to totalBytes,
            ))
        } catch (e: Exception) {
            Log.e(TAG, "ShardLock encode error", e)
            jsonResponse(500, mapOf("error" to "Encode failed: ${e.message}"))
        }
    }

    private fun handleShardlockHeartbeat(params: JSONObject): Response {
        if (!RustCore.isAvailable()) {
            return jsonResponse(503, mapOf("error" to "Rust-Core not available"))
        }

        val stats = storageManager.getStats()
        @Suppress("UNCHECKED_CAST")
        val roots = stats["merkleRoots"] as? List<String> ?: emptyList()

        val requestedRoot = params.optString("merkleRoot", "")
        val targetRoot = if (requestedRoot.isNotBlank()) requestedRoot else roots.lastOrNull()

        if (targetRoot.isNullOrBlank()) {
            return jsonResponse(400, mapOf("error" to "No shards stored. Store data first with /shardlock/encode."))
        }

        return try {
            // Decode hex root to bytes
            val rootBytes = targetRoot.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            
            // Fix: Use the shard count specific to this root, not the directory total
            val shardCount = storageManager.getShardCount(targetRoot)
            if (shardCount == 0) {
                return jsonResponse(404, mapOf("error" to "No shards found for root $targetRoot"))
            }

            // Generate a deterministic key for demo (in production, use Seed Vault TEE key)
            val demoKey = ByteArray(32) { (it + 1).toByte() }
            val signature = rustCore.generateHeartbeat(rootBytes, shardCount, demoKey)
            val sigHex = signature.joinToString("") { "%02x".format(it) }

            jsonResponse(200, mapOf(
                "merkleRoot" to targetRoot,
                "shardCount" to shardCount,
                "signature" to sigHex,
                "signatureLength" to signature.size,
                "timestamp" to System.currentTimeMillis(),
            ))
        } catch (e: Exception) {
            Log.e(TAG, "ShardLock heartbeat error", e)
            jsonResponse(500, mapOf("error" to "Heartbeat failed: ${e.message}"))
        }
    }

    private fun handleShardlockStatus(): Response {
        val stats = storageManager.getStats()
        val thermalStatus = if (RustCore.isAvailable()) {
            // Approximate thermal reading (real values come from device sensors)
            rustCore.getThermalStatus(40, 25)
        } else -1

        return jsonResponse(200, mapOf(
            "shardCount" to stats["shardCount"],
            "totalBytes" to stats["totalBytes"],
            "totalFormatted" to stats["totalFormatted"],
            "merkleRoots" to stats["merkleRoots"],
            "rootCount" to stats["rootCount"],
            "rustCoreAvailable" to RustCore.isAvailable(),
            "thermalStatus" to thermalStatus,
        ))
    }

    private fun handleShardlockClear(params: JSONObject): Response {
        val merkleRoot = params.optString("merkleRoot", "")
        if (merkleRoot.isBlank()) {
            return jsonResponse(400, mapOf("error" to "merkleRoot is required"))
        }

        val deleted = storageManager.clearShards(merkleRoot)
        
        // Also clear the meta file if it exists
        val metaFile = java.io.File(context.filesDir, "shardlock/$merkleRoot.meta")
        if (metaFile.exists()) metaFile.delete()

        return jsonResponse(200, mapOf(
            "success" to true,
            "merkleRoot" to merkleRoot,
            "deletedCount" to deleted,
        ))
    }

    private fun handleShardlockDecode(params: JSONObject): Response {
        if (!RustCore.isAvailable()) {
            return jsonResponse(503, mapOf("error" to "Rust-Core not available"))
        }

        val merkleRootHex = params.optString("merkleRoot", "")
        val dataShards = params.optInt("dataShards", 4)
        val parityShards = params.optInt("parityShards", 2)

        if (merkleRootHex.isBlank()) {
            return jsonResponse(400, mapOf("error" to "merkleRoot is required"))
        }

        return try {
            val totalShards = dataShards + parityShards
            val shards = storageManager.retrieveShards(merkleRootHex, totalShards)
            if (shards == null) {
                return jsonResponse(404, mapOf("error" to "Shards not found or incomplete for root $merkleRootHex"))
            }

            // Convert Array<ByteArray> to Array<ByteArray?> for the decode signature
            val shardsNullable = Array<ByteArray?>(shards.size) { shards[it] }
            val decodedBytes = rustCore.decode(shardsNullable, dataShards, parityShards)
            
            // Return as Base64 for binary safety
            val base64Data = android.util.Base64.encodeToString(decodedBytes, android.util.Base64.NO_WRAP)

            jsonResponse(200, mapOf(
                "merkleRoot" to merkleRootHex,
                "data" to base64Data,
                "encoding" to "base64",
            ))
        } catch (e: Exception) {
            Log.e(TAG, "ShardLock decode error", e)
            jsonResponse(500, mapOf("error" to "Decode failed: ${e.message}"))
        }
    }

    private fun handleShardlockReadMeta(params: JSONObject): Response {
        val merkleRoot = params.optString("merkleRoot", "")
        if (merkleRoot.isBlank()) {
            return jsonResponse(400, mapOf("error" to "merkleRoot is required"))
        }

        val metaFile = java.io.File(context.filesDir, "shardlock/$merkleRoot.meta")
        return if (metaFile.exists()) {
            try {
                val meta = JSONObject(metaFile.readText())
                jsonResponse(200, meta.toMap())
            } catch (e: Exception) {
                jsonResponse(500, mapOf("error" to "Failed to read meta: ${e.message}"))
            }
        } else {
            jsonResponse(404, mapOf("error" to "Metadata not found for $merkleRoot"))
        }
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = this.get(key)
        }
        return map
    }

    // Proxy /stats/db-summary to Node.js internal stats server (BAT-31)
    private fun proxyToNodeStats(): Response {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL("http://127.0.0.1:8766/stats/db-summary")
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val status = Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR
            newFixedLengthResponse(status, "application/json", body)
        } catch (e: Exception) {
            Log.w(TAG, "Stats proxy failed: ${e.message}")
            jsonResponse(503, mapOf("error" to "Stats unavailable"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun jsonResponse(status: Int, data: Map<String, Any?>): Response {
        val json = JSONObject(data).toString()
        return newFixedLengthResponse(
            Response.Status.lookup(status) ?: Response.Status.OK,
            "application/json",
            json
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        stop()
    }
}
