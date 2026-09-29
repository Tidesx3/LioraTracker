package app.liora.core.database

import androidx.room.Room

/** A throwaway database for JVM tests (the JVM target has no persistent production use). */
fun inMemoryLioraDatabase(): LioraDatabase = Room.inMemoryDatabaseBuilder<LioraDatabase>().buildDatabase()
