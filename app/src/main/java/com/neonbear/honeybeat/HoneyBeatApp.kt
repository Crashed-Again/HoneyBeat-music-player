package com.neonbear.honeybeat

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class HoneyBeatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
        NewPipe.init(NpDownloader(Net.client), Localization("en", "US"), ContentCountry("US"))
    }
}
