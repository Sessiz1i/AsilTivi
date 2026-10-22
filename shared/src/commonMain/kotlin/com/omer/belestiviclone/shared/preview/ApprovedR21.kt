package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

/** Exact, publisher-supported two-entry manifest. Query and explicit headers remain unchanged. */
object ApprovedR21 {
    val manifest = TransitionManifest(VerifiedDefinitionTransition.version,
        "977b055b92522963048cb12536e5af7da6b0a8aa1cb38d03fe4cad5725221882", VerifiedDefinitionTransition.session, VerifiedDefinitionTransition.seedSha256,
        listOf(
            DefinitionTransition("R20-C002",
                PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/e93b8e86-e4fc-450a-81d4-2188eda7dda0.m3u8"), listOf()),
                PlaybackDefinition(Address.fromForm("https://tv8.daioncdn.net/tv8/tv8.m3u8?app=7ddc255a-ef47-4e81-ab14-c0e5f2949788&ce=3"), listOf(Header("User-Agent", "Mozilla/5.0")))),
            DefinitionTransition("R20-C003",
                PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/aa1ae31d-1d61-44b2-b675-b74e3ff5c7fc.m3u8"), listOf()),
                PlaybackDefinition(Address.fromForm("https://tv-trthaber.medya.trt.com.tr/master.m3u8"), listOf(Header("User-Agent", "Mozilla/5.0"))))
        ))
}
