package com.etoken

import android.app.Application
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

class EtokenApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Card art is hotlinked from Scryfall's and Moxfield's CDNs. Scryfall's
     * rejects OkHttp's default User-Agent as bot traffic with an HTTP 400, so
     * every image request has to carry a descriptive one — the same fix
     * commander-companion's Android app carries, and not something the failure
     * mode makes obvious (images simply never load).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val registry = ComponentRegistry.Builder()
            .add(
                OkHttpNetworkFetcherFactory(
                    callFactory = {
                        OkHttpClient.Builder()
                            .addNetworkInterceptor { chain ->
                                chain.proceed(
                                    chain.request().newBuilder()
                                        .header("User-Agent", IMAGE_USER_AGENT)
                                        .build(),
                                )
                            }
                            .build()
                    },
                ),
                Uri::class,
            )
            .build()

        return ImageLoader.Builder(context)
            .components(registry)
            .build()
    }

    private companion object {
        const val IMAGE_USER_AGENT = "etoken-Android/0.1"
    }
}
