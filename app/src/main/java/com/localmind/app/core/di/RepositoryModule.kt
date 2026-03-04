package com.localmind.app.core.di

// Repositories use @Inject constructor + @Singleton annotations
// so Hilt automatically knows how to create them.
// No explicit @Provides methods needed - Hilt handles dependency injection
// for ModelRepository and ChatRepository via constructor injection.
