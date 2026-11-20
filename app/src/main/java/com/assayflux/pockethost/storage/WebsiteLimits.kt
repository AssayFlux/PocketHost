package com.assayflux.pockethost.storage

/** Shared safety budgets for every website ingress, management and backup path. */
object WebsiteLimits {
    const val MAX_FILES = 5_000
    const val MAX_ENTRIES = 10_000
    const val MAX_TOTAL_BYTES = 500_000_000L
    const val MAX_SINGLE_FILE_BYTES = MAX_TOTAL_BYTES
    const val MAX_DEPTH = 32
    const val MAX_EDIT_BYTES = 256L * 1024L
    const val MAX_REGISTERED_SITES = 100
    const val MAX_HOSTNAMES_PER_SITE = 100
    const val MAX_HOSTNAME_ROUTES = 1_000
}
