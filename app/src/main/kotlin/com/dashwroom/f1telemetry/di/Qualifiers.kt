package com.dashwroom.f1telemetry.di

import javax.inject.Qualifier

/** Process-lifetime scope (SupervisorJob + Default). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** The single dedicated thread that receives, parses and applies telemetry. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IngestDispatcher
