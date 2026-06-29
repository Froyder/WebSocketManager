package io.github.froyder.websocketmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MarketScreen(
    viewModel: MarketViewModel = hiltViewModel()
) {
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val selectedSymbols by viewModel.selectedSymbols.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        StatusBar(
            state = marketState,
            onConnect = { viewModel.processIntent(MarketIntent.Connect) },
            onDisconnect = { viewModel.processIntent(MarketIntent.Disconnect) },
            onRetry = { viewModel.processIntent(MarketIntent.Retry) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SymbolSelector(
            selectedSymbols = selectedSymbols,
            onSymbolToggled = { symbol ->
                val updated = if (symbol in selectedSymbols) {
                    selectedSymbols - symbol
                } else {
                    selectedSymbols + symbol
                }
                viewModel.processIntent(MarketIntent.UpdateSubscription(updated))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(coins, key = { it.symbol }) { coin ->
                CoinItem(coin = coin)
            }
        }
    }
}

@Composable
private fun StatusBar(
    state: MarketState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (text, color) = when (state) {
            is MarketState.Idle -> "Idle" to Color.Gray
            is MarketState.Connecting -> "Connecting..." to Color.Yellow
            is MarketState.Connected -> "Live" to Color.Green
            is MarketState.Reconnecting -> "Reconnecting (${state.attempt})..." to Color.Yellow
            is MarketState.Disconnected -> "Disconnected" to Color.Gray
            is MarketState.Error -> "Error: ${state.message}" to Color.Red
        }

        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge)

        when (state) {
            is MarketState.Connected -> {
                Button(onClick = onDisconnect) { Text("Disconnect") }
            }
            is MarketState.Disconnected, is MarketState.Error, is MarketState.Idle -> {
                Button(onClick = onConnect) { Text("Connect") }
            }
            is MarketState.Reconnecting -> {
                Button(onClick = onRetry) { Text("Retry now") }
            }
            else -> {}
        }
    }
}

@Composable
private fun SymbolSelector(
    selectedSymbols: Set<String>,
    onSymbolToggled: (String) -> Unit
) {
    val allSymbols = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "ADAUSDT")

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(allSymbols) { symbol ->
            FilterChip(
                selected = symbol in selectedSymbols,
                onClick = { onSymbolToggled(symbol) },
                label = { Text(symbol.removeSuffix("USDT")) }
            )
        }
    }
}

@Composable
private fun CoinItem(coin: CoinUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = coin.symbol.removeSuffix("USDT"),
                style = MaterialTheme.typography.titleMedium
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${coin.price}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${if (coin.isPositive) "+" else ""}${coin.priceChangePercent}%",
                    color = if (coin.isPositive) Color.Green else Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}