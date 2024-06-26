package com.dikamahard.presensi

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DailyViewModel : ViewModel() {
    // TODO: Implement the ViewModel

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    fun updateDate(date: String) {
        _selectedDate.value = date
    }
}