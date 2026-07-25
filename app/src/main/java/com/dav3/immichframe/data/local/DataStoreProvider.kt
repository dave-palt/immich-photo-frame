package com.dav3.immichframe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single shared DataStore instance — prevents "multiple DataStores" crash. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore("settings")
