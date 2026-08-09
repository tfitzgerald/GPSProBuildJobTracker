package ca.gpsprobuild.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.gpsprobuild.app.domain.model.DeviceRole
import ca.gpsprobuild.app.domain.model.PrivacyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gpsprobuild_settings")

/** Immutable snapshot of everything in DataStore, so screens observe one object. */
data class AppSettings(
    // Device identity
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceRole: DeviceRole = DeviceRole.OWNER,
    val setupComplete: Boolean = false,

    // Company profile — feeds PDF headers
    val businessName: String = "GPS Probuild Inc.",
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessWebsite: String = "",
    val businessStreet: String = "",
    val businessCity: String = "Pickering",
    val businessProvince: String = "ON",
    val businessPostalCode: String = "",
    val hstNumber: String = "",

    // Financial defaults
    val taxRatePercent: Double = 13.0,
    val defaultLabourRateCents: Long = 0,
    val defaultMarkupPercent: Double = 0.0,
    val quoteTermsText: String = "",

    // Job numbering — owner device only
    val jobNumberPrefix: String = "GPB",
    val jobNumberNext: Int = 1,

    // Appearance and privacy
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val privacyMode: PrivacyMode = PrivacyMode.FULL,
    val privacyAutoRevert: Boolean = true,

    // Photos
    val geotagPhotos: Boolean = false,
    val photoLongEdgePx: Int = 2560,
    val photoQuality: Int = 85,

    // Sync
    val lastExportAtMillis: Long = 0,
    val lastImportAtMillis: Long = 0,
    val lastBackupAtMillis: Long = 0,
    val packetIncludesPhotos: Boolean = false,

    // Updates
    val updateCheckEnabled: Boolean = true,
    val lastUpdateCheckMillis: Long = 0
) {
    val isOwner: Boolean get() = deviceRole == DeviceRole.OWNER
    val isField: Boolean get() = deviceRole == DeviceRole.FIELD

    /**
     * Field devices are pinned to client-safe regardless of what is stored, and
     * the cost figures are not in their database to begin with. Belt and braces.
     */
    val effectivePrivacyMode: PrivacyMode
        get() = if (isField && privacyMode == PrivacyMode.FULL) PrivacyMode.CLIENT_SAFE else privacyMode
}

enum class ThemeMode(val label: String) { SYSTEM("Match system"), LIGHT("Light"), DARK("Dark") }

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_ROLE = stringPreferencesKey("device_role")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val PIN_HASH = stringPreferencesKey("owner_pin_hash")
        val PIN_SALT = stringPreferencesKey("owner_pin_salt")

        val BUSINESS_NAME = stringPreferencesKey("business_name")
        val BUSINESS_PHONE = stringPreferencesKey("business_phone")
        val BUSINESS_EMAIL = stringPreferencesKey("business_email")
        val BUSINESS_WEBSITE = stringPreferencesKey("business_website")
        val BUSINESS_STREET = stringPreferencesKey("business_street")
        val BUSINESS_CITY = stringPreferencesKey("business_city")
        val BUSINESS_PROVINCE = stringPreferencesKey("business_province")
        val BUSINESS_POSTAL = stringPreferencesKey("business_postal")
        val HST_NUMBER = stringPreferencesKey("hst_number")

        val TAX_RATE = stringPreferencesKey("tax_rate")
        val LABOUR_RATE = longPreferencesKey("labour_rate_cents")
        val MARKUP = stringPreferencesKey("markup_percent")
        val QUOTE_TERMS = stringPreferencesKey("quote_terms")

        val JOB_PREFIX = stringPreferencesKey("job_prefix")
        val JOB_NEXT = intPreferencesKey("job_next")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIVACY_MODE = stringPreferencesKey("privacy_mode")
        val PRIVACY_AUTO_REVERT = booleanPreferencesKey("privacy_auto_revert")

        val GEOTAG = booleanPreferencesKey("geotag_photos")
        val PHOTO_EDGE = intPreferencesKey("photo_long_edge")
        val PHOTO_QUALITY = intPreferencesKey("photo_quality")

        val LAST_EXPORT = longPreferencesKey("last_export_at")
        val LAST_IMPORT = longPreferencesKey("last_import_at")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val PACKET_PHOTOS = booleanPreferencesKey("packet_includes_photos")

        val UPDATE_CHECK = booleanPreferencesKey("update_check_enabled")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            deviceId = p[Keys.DEVICE_ID].orEmpty(),
            deviceName = p[Keys.DEVICE_NAME].orEmpty(),
            deviceRole = p[Keys.DEVICE_ROLE]?.let { runCatching { DeviceRole.valueOf(it) }.getOrNull() }
                ?: DeviceRole.OWNER,
            setupComplete = p[Keys.SETUP_COMPLETE] ?: false,

            businessName = p[Keys.BUSINESS_NAME] ?: "GPS Probuild Inc.",
            businessPhone = p[Keys.BUSINESS_PHONE].orEmpty(),
            businessEmail = p[Keys.BUSINESS_EMAIL].orEmpty(),
            businessWebsite = p[Keys.BUSINESS_WEBSITE].orEmpty(),
            businessStreet = p[Keys.BUSINESS_STREET].orEmpty(),
            businessCity = p[Keys.BUSINESS_CITY] ?: "Pickering",
            businessProvince = p[Keys.BUSINESS_PROVINCE] ?: "ON",
            businessPostalCode = p[Keys.BUSINESS_POSTAL].orEmpty(),
            hstNumber = p[Keys.HST_NUMBER].orEmpty(),

            taxRatePercent = p[Keys.TAX_RATE]?.toDoubleOrNull() ?: 13.0,
            defaultLabourRateCents = p[Keys.LABOUR_RATE] ?: 0L,
            defaultMarkupPercent = p[Keys.MARKUP]?.toDoubleOrNull() ?: 0.0,
            quoteTermsText = p[Keys.QUOTE_TERMS].orEmpty(),

            jobNumberPrefix = p[Keys.JOB_PREFIX] ?: "GPB",
            jobNumberNext = p[Keys.JOB_NEXT] ?: 1,

            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            privacyMode = p[Keys.PRIVACY_MODE]?.let { runCatching { PrivacyMode.valueOf(it) }.getOrNull() }
                ?: PrivacyMode.FULL,
            privacyAutoRevert = p[Keys.PRIVACY_AUTO_REVERT] ?: true,

            geotagPhotos = p[Keys.GEOTAG] ?: false,
            photoLongEdgePx = p[Keys.PHOTO_EDGE] ?: 2560,
            photoQuality = p[Keys.PHOTO_QUALITY] ?: 85,

            lastExportAtMillis = p[Keys.LAST_EXPORT] ?: 0L,
            lastImportAtMillis = p[Keys.LAST_IMPORT] ?: 0L,
            lastBackupAtMillis = p[Keys.LAST_BACKUP] ?: 0L,
            packetIncludesPhotos = p[Keys.PACKET_PHOTOS] ?: false,

            updateCheckEnabled = p[Keys.UPDATE_CHECK] ?: true,
            lastUpdateCheckMillis = p[Keys.LAST_UPDATE_CHECK] ?: 0L
        )
    }

    /**
     * Generates the device UUID once, on first launch, and never again. Everything
     * in the sync layer hangs off this value.
     */
    suspend fun ensureDeviceId(): String {
        val existing = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun completeSetup(deviceName: String, role: DeviceRole) {
        context.dataStore.edit {
            it[Keys.DEVICE_NAME] = deviceName
            it[Keys.DEVICE_ROLE] = role.name
            it[Keys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setDeviceName(name: String) =
        context.dataStore.edit { it[Keys.DEVICE_NAME] = name }

    suspend fun setPrivacyMode(mode: PrivacyMode) =
        context.dataStore.edit { it[Keys.PRIVACY_MODE] = mode.name }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setGeotagPhotos(enabled: Boolean) =
        context.dataStore.edit { it[Keys.GEOTAG] = enabled }

    suspend fun setUpdateCheckEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.UPDATE_CHECK] = enabled }

    suspend fun updateCompanyProfile(
        name: String, phone: String, email: String, website: String,
        street: String, city: String, province: String, postal: String, hst: String
    ) = context.dataStore.edit {
        it[Keys.BUSINESS_NAME] = name
        it[Keys.BUSINESS_PHONE] = phone
        it[Keys.BUSINESS_EMAIL] = email
        it[Keys.BUSINESS_WEBSITE] = website
        it[Keys.BUSINESS_STREET] = street
        it[Keys.BUSINESS_CITY] = city
        it[Keys.BUSINESS_PROVINCE] = province
        it[Keys.BUSINESS_POSTAL] = postal
        it[Keys.HST_NUMBER] = hst
    }

    suspend fun setJobNumbering(prefix: String, next: Int) = context.dataStore.edit {
        it[Keys.JOB_PREFIX] = prefix
        it[Keys.JOB_NEXT] = next
    }

    /**
     * Reserves the next job number atomically and returns the formatted value.
     * Owner device only — field devices never touch this counter, which removes
     * the nastiest class of sync collision.
     */
    suspend fun reserveJobNumber(year: Int): String {
        var result = ""
        context.dataStore.edit { prefs ->
            val prefix = prefs[Keys.JOB_PREFIX] ?: "GPB"
            val next = prefs[Keys.JOB_NEXT] ?: 1
            result = "%s-%d-%04d".format(prefix, year, next)
            prefs[Keys.JOB_NEXT] = next + 1
        }
        return result
    }

    suspend fun markBackupTaken(atMillis: Long) =
        context.dataStore.edit { it[Keys.LAST_BACKUP] = atMillis }

    suspend fun markExport(atMillis: Long) =
        context.dataStore.edit { it[Keys.LAST_EXPORT] = atMillis }

    suspend fun markImport(atMillis: Long) =
        context.dataStore.edit { it[Keys.LAST_IMPORT] = atMillis }

    // --- Owner PIN ---------------------------------------------------------
    // Guards the switch from Field back to Owner. PBKDF2 with a per-install salt:
    // this is not protecting state secrets, it is stopping a curious crew member
    // from flipping a toggle and reading margins.

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun setOwnerPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = Base64.encode(salt)
            it[Keys.PIN_HASH] = Base64.encode(hash)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun verifyOwnerPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val saltEncoded = prefs[Keys.PIN_SALT] ?: return false
        val hashEncoded = prefs[Keys.PIN_HASH] ?: return false
        val computed = pbkdf2(pin, Base64.decode(saltEncoded))
        return computed.contentEquals(Base64.decode(hashEncoded))
    }

    suspend fun hasOwnerPin(): Boolean =
        !context.dataStore.data.first()[Keys.PIN_HASH].isNullOrBlank()

    suspend fun switchRole(role: DeviceRole) =
        context.dataStore.edit { it[Keys.DEVICE_ROLE] = role.name }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
