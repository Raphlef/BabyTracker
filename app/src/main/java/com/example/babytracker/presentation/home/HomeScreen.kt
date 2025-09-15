package com.example.babytracker.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(listState: LazyListState) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(20) { Text("Home item #$it", Modifier.padding(16.dp)) }
    }
}