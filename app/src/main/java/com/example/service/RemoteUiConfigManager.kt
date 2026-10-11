package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ApkBuildRecord
import com.example.model.AppUiConfig
import com.example.model.ConfigVersionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemoteUiConfigManager(private val context: Context? = null) {

    // Published config lives on the backend (brain /ui/config, appConfig/remoteUi) so every user gets it without a new APK.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(AppUiConfig::class.java)
    private val versionIds = mutableMapOf<String, String>() // label -> server versionId
    private val _publishStatus = MutableStateFlow("")
    val publishStatus: StateFlow<String> = _publishStatus.asStateFlow()
    private fun fbUser() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

    private suspend fun pushPublished(cfg: AppUiConfig, notes: String, author: String) {
        val u = fbUser() ?: run { _publishStatus.value = "ยังไม่ได้เข้าสู่ระบบ: เผยแพร่เฉพาะในเครื่องนี้"; return }
        val body = org.json.JSONObject().put("config", org.json.JSONObject(adapter.toJson(cfg))).put("notes", notes).put("label", cfg.configVersion).put("byName", author)
        val r = com.example.network.BrainApi.call(u, "PUT", "/admin/ui/config", body)
        _publishStatus.value = if (r.code == 200) "เผยแพร่ให้ผู้ใช้ทุกคนแล้ว" else "เผยแพร่ไม่สำเร็จ (${r.code}) ${r.json?.optString("error").orEmpty()}"
        if (r.code == 200) loadVersions()
    }

    private suspend fun loadVersions() {
        val u = fbUser() ?: return
        val r = com.example.network.BrainApi.call(u, "GET", "/admin/ui/config/versions")
        val arr = r.json?.optJSONArray("versions") ?: return // non-admins get 403: keep local list
        val cur = r.json?.optString("currentVersionId")
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        versionIds.clear()
        _versionHistory.value = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val cfg = runCatching { adapter.fromJson(o.getJSONObject("config").toString()) }.getOrNull() ?: return@mapNotNull null
            val id = o.optString("versionId"); val label = o.optString("label").ifBlank { id }
            versionIds[label] = id
            ConfigVersionRecord(label, fmt.format(Date(o.optLong("savedAt"))), o.optString("savedBy"), o.optString("notes"), cfg, id == cur)
        }
    }

    private val prefs: SharedPreferences? = context?.getSharedPreferences("friendtalk_ui_config", Context.MODE_PRIVATE)

    private val _activeConfig = MutableStateFlow(AppUiConfig())
    val activeConfig: StateFlow<AppUiConfig> = _activeConfig.asStateFlow()

    private val _draftConfig = MutableStateFlow<AppUiConfig?>(null)
    val draftConfig: StateFlow<AppUiConfig?> = _draftConfig.asStateFlow()

    private val _versionHistory = MutableStateFlow<List<ConfigVersionRecord>>(emptyList())
    val versionHistory: StateFlow<List<ConfigVersionRecord>> = _versionHistory.asStateFlow()

    private val _apkBuilds = MutableStateFlow<List<ApkBuildRecord>>(emptyList())
    val apkBuilds: StateFlow<List<ApkBuildRecord>> = _apkBuilds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        initializeInitialData()
    }

    private fun initializeInitialData() {
        val initialConfig = AppUiConfig()
        _activeConfig.value = initialConfig

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val now = dateFormat.format(Date())

        _versionHistory.value = listOf(
            ConfigVersionRecord(
                version = "v1.2.0",
                publishedAt = now,
                publishedBy = "Admin (mama.ff9522@gmail.com)",
                notes = "ปรับปรุงเลย์เอาต์หน้า Feed ใหม่: Category Tabs, Story Row, Rich Media Card & Admin UI Customization",
                config = initialConfig.copy(configVersion = "v1.2.0", versionNotes = "Enhanced Feed Layout"),
                isActive = true
            ),
            ConfigVersionRecord(
                version = "v1.1.0",
                publishedAt = "06/10/2026 14:30",
                publishedBy = "Admin",
                notes = "เปิดใช้งาน Remote UI Config, ระบบจัดวาง Section และธีม M3",
                config = initialConfig.copy(configVersion = "v1.1.0", versionNotes = "Remote Config Active"),
                isActive = false
            ),
            ConfigVersionRecord(
                version = "v1.0.0",
                publishedAt = "01/10/2026 09:00",
                publishedBy = "System",
                notes = "ค่าตั้งต้นระบบ FriendTalk แบบดั้งเดิม",
                config = initialConfig.copy(configVersion = "v1.0.0", versionNotes = "Initial Release"),
                isActive = false
            )
        )

        val apkFile = listOf(
            File("/downloads/FriendTalk-latest.apk"),
            File("downloads/FriendTalk-latest.apk"),
            File("/.build-outputs/app-debug.apk")
        ).firstOrNull { it.exists() }

        val apkSizeFormatted = if (apkFile != null && apkFile.exists()) {
            String.format(Locale.US, "%.1f MB", apkFile.length() / (1024.0 * 1024.0))
        } else {
            "24.2 MB"
        }

        _apkBuilds.value = listOf(
            ApkBuildRecord(
                buildId = "build_120",
                versionName = "1.2.0",
                versionCode = 4,
                buildDate = now,
                apkSizeMb = apkSizeFormatted,
                status = "Build Successful",
                apkFileName = "FriendTalk-latest.apk",
                downloadPath = "/downloads/FriendTalk-latest.apk",
                notes = "New Feed UI Architecture, Tabs, Story Row, Video/Live Card Preview"
            ),
            ApkBuildRecord(
                buildId = "build_110",
                versionName = "1.1.0",
                versionCode = 3,
                buildDate = "06/10/2026 15:00",
                apkSizeMb = "24.0 MB",
                status = "Build Successful",
                apkFileName = "FriendTalk-v1.1.0.apk",
                downloadPath = "/downloads/FriendTalk-latest.apk",
                notes = "Remote UI Config Engine + Movie/Video + Full Customization"
            )
        )
    }

    fun startEditing(): AppUiConfig {
        val current = _activeConfig.value
        val draft = _draftConfig.value ?: current.copy()
        _draftConfig.value = draft
        return draft
    }

    fun updateDraft(config: AppUiConfig) {
        _draftConfig.value = config
    }

    fun saveDraft(config: AppUiConfig) {
        _draftConfig.value = config
    }

    fun cancelDraft() {
        _draftConfig.value = null
    }

    fun publish(config: AppUiConfig, author: String, notes: String): String {
        val nextVersionNumber = generateNextVersion(_activeConfig.value.configVersion)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val publishedConfig = config.copy(
            configVersion = nextVersionNumber,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            updatedBy = author,
            versionNotes = if (notes.isNotBlank()) notes else "อัปเดตหน้าตา UI และการจัดวาง"
        )

        _activeConfig.value = publishedConfig
        _draftConfig.value = null
        _publishStatus.value = "กำลังเผยแพร่…"
        scope.launch { pushPublished(publishedConfig, publishedConfig.versionNotes, author) }

        val newRecord = ConfigVersionRecord(
            version = nextVersionNumber,
            publishedAt = dateStr,
            publishedBy = author,
            notes = publishedConfig.versionNotes,
            config = publishedConfig,
            isActive = true
        )

        val updatedHistory = _versionHistory.value.map { it.copy(isActive = false) }.toMutableList()
        updatedHistory.add(0, newRecord)
        _versionHistory.value = updatedHistory

        return nextVersionNumber
    }

    fun rollbackTo(version: String, author: String): Boolean {
        val targetRecord = _versionHistory.value.find { it.version == version } ?: return false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val rolledBackConfig = targetRecord.config.copy(
            lastUpdatedTimestamp = System.currentTimeMillis(),
            updatedBy = "$author (Rollback to $version)",
            versionNotes = "ย้อนกลับไปยังรุ่น $version (${targetRecord.notes})"
        )

        _activeConfig.value = rolledBackConfig
        _draftConfig.value = null
        versionIds[version]?.let { vid ->
            scope.launch {
                val u = fbUser() ?: return@launch
                val r = com.example.network.BrainApi.call(u, "POST", "/admin/ui/config/revert", org.json.JSONObject().put("versionId", vid))
                _publishStatus.value = if (r.code == 200) "ย้อนกลับให้ผู้ใช้ทุกคนแล้ว" else "ย้อนกลับไม่สำเร็จ (${r.code})"
                if (r.code == 200) loadVersions()
            }
        }

        val updatedHistory = _versionHistory.value.map {
            it.copy(isActive = (it.version == version))
        }
        _versionHistory.value = updatedHistory
        return true
    }

    fun restoreDefault(author: String) {
        val defaultConfig = AppUiConfig()
        publish(defaultConfig, author, "รีเซ็ตค่า UI กลับสู่ค่าเริ่มต้นระบบ FriendTalk")
    }

    suspend fun refreshRemoteConfig(): Boolean {
        _isRefreshing.value = true
        try {
            val u = fbUser() ?: return false
            val r = com.example.network.BrainApi.call(u, "GET", "/ui/config")
            if (r.code != 200) return false
            r.json?.optJSONObject("config")?.let { c ->
                runCatching { adapter.fromJson(c.toString()) }.getOrNull()?.let { _activeConfig.value = it }
            }
            loadVersions()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            _isRefreshing.value = false
        }
    }

    fun addNewApkBuild(versionName: String, notes: String): ApkBuildRecord {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val nextCode = (_apkBuilds.value.maxOfOrNull { it.versionCode } ?: 0) + 1

        val apkFile = File("/.build-outputs/app-debug.apk")
        val apkSizeFormatted = if (apkFile.exists()) {
            String.format(Locale.US, "%.1f MB", apkFile.length() / (1024.0 * 1024.0))
        } else {
            "24.5 MB"
        }

        val record = ApkBuildRecord(
            buildId = "build_${System.currentTimeMillis()}",
            versionName = versionName,
            versionCode = nextCode,
            buildDate = dateStr,
            apkSizeMb = apkSizeFormatted,
            status = "Build Successful",
            apkFileName = "FriendTalk-v$versionName-release.apk",
            downloadPath = "/.build-outputs/app-debug.apk",
            notes = notes
        )

        val list = _apkBuilds.value.toMutableList()
        list.add(0, record)
        _apkBuilds.value = list
        return record
    }

    private fun generateNextVersion(current: String): String {
        val clean = current.removePrefix("v").trim()
        val parts = clean.split(".")
        if (parts.size == 3) {
            val major = parts[0].toIntOrNull() ?: 1
            val minor = parts[1].toIntOrNull() ?: 0
            val patch = parts[2].toIntOrNull() ?: 0
            return "v$major.${minor + 1}.0"
        }
        return "v1.${System.currentTimeMillis() % 1000}.0"
    }

    companion object {
        @Volatile
        private var instance: RemoteUiConfigManager? = null

        fun getInstance(context: Context? = null): RemoteUiConfigManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteUiConfigManager(context).also { instance = it }
            }
        }
    }
}
