package ir.deweyshelf.app

import android.app.Application
import ir.deweyshelf.app.data.DeweyDatabase
import ir.deweyshelf.app.data.RoomBookRepository

class DeweyApplication : Application() {
    val database: DeweyDatabase by lazy { DeweyDatabase.create(this) }
    val repository: RoomBookRepository by lazy { RoomBookRepository(database) }
}

