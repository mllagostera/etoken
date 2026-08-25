package com.etoken

import android.content.Context
import com.etoken.data.MoxfieldRepository
import com.etoken.data.Network
import com.etoken.data.UserPreferences

/**
 * Hand-rolled dependency container.
 *
 * commander-companion's app uses Hilt, which earns its keep there across many
 * feature modules; this app has one repository and one preferences store, so a
 * container built once in [EtokenApplication] keeps the build free of KSP.
 */
class AppContainer(context: Context) {

    val repository: MoxfieldRepository by lazy {
        MoxfieldRepository(
            moxfield = Network.moxfieldApi(),
            scryfall = Network.scryfallApi(),
        )
    }

    val userPreferences: UserPreferences by lazy { UserPreferences(context) }
}
