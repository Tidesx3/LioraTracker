package app.liora.feature.walkthrough

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The first-launch walkthrough has no route: the app shell shows [WalkthroughScreen] in its place while
 * `WalkthroughRepository.pending` says so, and comes back when it's finished.
 */
val walkthroughModule =
    module {
        viewModelOf(::WalkthroughViewModel)
    }
