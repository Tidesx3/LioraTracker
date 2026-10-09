package app.liora.integration.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Liora's releases in [repo] ("owner/name"), over OkHttp. */
fun gitHubReleases(repo: String): GitHubReleases = GitHubReleases(HttpClient(OkHttp), repo)
