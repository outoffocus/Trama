package com.trama.app.location

import android.content.Context
import android.content.Intent
import android.net.Uri

object PlaceMapsLauncher {
    fun openInGoogleMaps(context: Context, latitude: Double, longitude: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val url = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

        val googleIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
        } else {
            context.startActivity(fallbackIntent)
        }
    }
}
