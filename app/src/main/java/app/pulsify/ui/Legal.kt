package app.pulsify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** The in-app copies of the Privacy Policy and Terms (the hosted versions are the same text). */
enum class LegalDoc(val title: String, val updated: String, val sections: List<Pair<String, String>>) {
    PRIVACY(
        "Privacy Policy", "Last updated 22 November 2026",
        listOf(
            "Who we are" to "Screenpals is made by Neutrogit. In this policy, \"we\" and \"us\" means Neutrogit.",
            "The short version" to "Screenpals works offline. The app has no internet permission, no ads, no analytics and no account. Your wallpaper, settings and creatures stay on your phone.",
            "Music reaction" to "When you turn on \"React to my music\", Android asks for the microphone permission because the Visualizer that reads what your phone is playing sits behind it. Screenpals only reads the loudness of the bass, mids and highs, frame by frame, to move the wallpaper. Nothing is recorded, saved or sent anywhere. You can turn it off in Settings at any time.",
            "What stays on your phone" to "Your chosen creature, style, world, colours and settings are kept in the app's private storage so the wallpaper looks the same after a restart. If something crashes, a short technical log is kept on the phone; it only leaves the phone if you copy it yourself with \"Copy diagnostics\".",
            "Purchases" to "If you buy Screenpals Pro or a pack, the payment is handled by Google Play. We receive only what Google shares with developers to confirm the purchase, never your card details.",
            "Create your own (coming later)" to "If you choose to turn a photo into your own 3D wallpaper, that photo is sent over an encrypted connection to our server and our 3D provider only to build your model, and is deleted once the model is ready. We will ask for your permission before the first upload.",
            "Children" to "Screenpals is safe for all ages and doesn't collect personal information from anyone. The photo feature is for users 13 and older.",
            "Your choices" to "You can turn off music reaction, clear the app's data, or uninstall Screenpals at any time, which removes everything it stored.",
            "Changes and contact" to "If this policy changes we will update the date above and tell you in the app. Questions: [support email].",
        ),
    ),
    TERMS(
        "Terms of Service", "Last updated 22 November 2026",
        listOf(
            "Who we are" to "Screenpals is made and published by Neutrogit. In these terms, \"we\" and \"us\" means Neutrogit.",
            "Agreement" to "By installing or using Screenpals you agree to these terms. If you are under the age of majority where you live, ask a parent or guardian before buying anything.",
            "Your license" to "You may use Screenpals on your own devices for personal, non-commercial use. The creatures, statues, artwork, animations, sounds and code belong to us or our licensors; please don't copy, resell, extract or reverse-engineer them. Screenshots and recordings of your own home screen are fine.",
            "Purchases" to "Payments go through Google Play. Subscriptions renew until you cancel in Google Play, and you keep Pro until the end of the period you paid for. Free trials become paid unless cancelled before they end. Lifetime purchases and packs stay yours on the same Google account. Credits for Create your own are used when a model is generated; a failure on our side gives the credit back. Refunds follow Google Play's policy.",
            "Photos you upload" to "When Create your own is available, you keep ownership of your photos and the wallpapers made from them. Only upload photos you own or may use, and nothing illegal, hateful, sexual or that infringes someone's rights.",
            "No warranty" to "Screenpals is provided as is. We work to keep it smooth and battery-friendly, but can't promise it runs without errors on every phone and launcher.",
            "Liability" to "To the extent the law allows, we are not liable for indirect losses, and our total liability is limited to what you paid us in the 12 months before a claim. Your consumer rights are not affected.",
            "Law" to "These terms are governed by the laws of the Arab Republic of Egypt, without affecting consumer protections where you live.",
            "Changes and contact" to "We may update these terms and will tell you in the app about important changes. Questions: [support email].",
        ),
    ),
}

@Composable
fun LegalPage(doc: LegalDoc, onBack: () -> Unit) {
    val c = LocalPulse.current
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
    ) {
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Text(doc.title, style = T.title, color = c.text)
        }
        Gap(8.dp)
        Text(doc.updated, style = T.small, color = c.faint)
        Gap(18.dp)
        doc.sections.forEach { (h, body) ->
            MicroLabel(h)
            Gap(6.dp)
            Text(body, style = T.body, color = c.sub)
            Gap(18.dp)
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
    }
}
