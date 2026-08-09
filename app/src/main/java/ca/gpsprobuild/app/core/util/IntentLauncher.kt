package ca.gpsprobuild.app.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Launching the phone's own apps rather than reimplementing them. ACTION_DIAL is
 * used instead of ACTION_CALL on purpose — it opens the dialler with the number
 * filled in and needs no CALL_PHONE permission, and nobody wants an app that can
 * place calls on its own.
 */
object IntentLauncher {

    fun dial(context: Context, phone: String?) {
        val number = Phones.dialable(phone)
        if (number.isBlank()) return
        safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "No dialler app found")
    }

    fun sms(context: Context, phone: String?, body: String? = null) {
        val number = Phones.dialable(phone)
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            if (!body.isNullOrBlank()) putExtra("sms_body", body)
        }
        safeStart(context, intent, "No messaging app found")
    }

    fun email(context: Context, address: String?, subject: String? = null, body: String? = null) {
        if (address.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, body)
        }
        safeStart(context, intent, "No email app found")
    }

    /** Opens the address in whatever maps app is installed, with turn-by-turn ready. */
    fun directions(context: Context, address: String?) {
        if (address.isNullOrBlank()) return
        val encoded = Uri.encode(address)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Fall back to the browser rather than failing silently.
            safeStart(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$encoded")),
                "No maps app found"
            )
        }
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        safeStart(context, Intent.createChooser(intent, "Share"), "Nothing to share with")
    }

    private fun safeStart(context: Context, intent: Intent, failureMessage: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Builds a one-line address for maps and for display. */
object Addresses {
    fun oneLine(
        street1: String?, street2: String?, city: String?,
        province: String?, postal: String?
    ): String = listOfNotNull(
        street1?.takeIf { it.isNotBlank() },
        street2?.takeIf { it.isNotBlank() },
        city?.takeIf { it.isNotBlank() },
        province?.takeIf { it.isNotBlank() },
        postal?.takeIf { it.isNotBlank() }
    ).joinToString(", ")

    fun multiLine(
        street1: String?, street2: String?, city: String?,
        province: String?, postal: String?
    ): String {
        val line1 = listOfNotNull(
            street1?.takeIf { it.isNotBlank() },
            street2?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
        val line2 = listOfNotNull(
            city?.takeIf { it.isNotBlank() },
            province?.takeIf { it.isNotBlank() },
            postal?.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        return listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n")
    }
}
