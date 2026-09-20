package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private data class ComparisonMetric(val label: String, val left: String, val right: String)

@Composable
fun CompanyComparison(stocks: List<Stock>, back: () -> Unit) {
    val available = stocks.distinctBy { it.symbol }.filter { it.symbol.isNotBlank() }
    if (available.size < 2) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column { Text("Compare companies", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Side-by-side sourced company data", fontSize = 10.sp, color = CompareMuted) }
                }
            }
            item { CompareNote("At least two companies are needed for a comparison.") }
        }
        return
    }
    var leftSymbol by rememberSaveable { mutableStateOf(available[0].symbol) }
    var rightSymbol by rememberSaveable { mutableStateOf(available[1].symbol) }
    if (leftSymbol == rightSymbol) available.firstOrNull { it.symbol != leftSymbol }?.let { rightSymbol = it.symbol }
    var leftResult by remember(leftSymbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var rightResult by remember(rightSymbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var loading by remember(leftSymbol, rightSymbol) { mutableStateOf(true) }
    LaunchedEffect(leftSymbol, rightSymbol) {
        loading = true
        coroutineScope {
            val results = listOf(async { CompanyIntelligenceCache.load(leftSymbol) }, async { CompanyIntelligenceCache.load(rightSymbol) }).awaitAll()
            leftResult = results[0]; rightResult = results[1]
        }
        loading = false
    }
    val leftStock = available.first { it.symbol == leftSymbol }
    val rightStock = available.first { it.symbol == rightSymbol }
    val metrics = listOf(
        ComparisonMetric("Price", "KSh %.2f".format(leftStock.price), "KSh %.2f".format(rightStock.price)),
        ComparisonMetric("Revenue", financial(leftResult.profile.revenue, leftResult.profile.financialUnit), financial(rightResult.profile.revenue, rightResult.profile.financialUnit)),
        ComparisonMetric("Profit", financial(leftResult.profile.profit, leftResult.profile.financialUnit), financial(rightResult.profile.profit, rightResult.profile.financialUnit)),
        ComparisonMetric("EPS", value(leftResult.profile.eps), value(rightResult.profile.eps)),
        ComparisonMetric("Revenue growth", percent(leftResult.profile.revenueGrowth), percent(rightResult.profile.revenueGrowth)),
        ComparisonMetric("Profit growth", percent(leftResult.profile.profitGrowth), percent(rightResult.profile.profitGrowth)),
        ComparisonMetric("P/E", value(leftResult.profile.pe), value(rightResult.profile.pe)),
        ComparisonMetric("P/B", value(leftResult.profile.pb), value(rightResult.profile.pb)),
        ComparisonMetric("ROE", percent(leftResult.profile.roe), percent(rightResult.profile.roe)),
        ComparisonMetric("Debt / Equity", value(leftResult.profile.debtToEquity), value(rightResult.profile.debtToEquity)),
        ComparisonMetric("Dividend yield", percent(leftResult.profile.dividendYield), percent(rightResult.profile.dividendYield))
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; Column(Modifier.weight(1f)) { Text("Compare companies", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Compare sourced figures side by side", fontSize = 10.sp, color = CompareMuted) }; Icon(Icons.Default.CompareArrows, null, tint = CompareGreen, modifier = Modifier.size(24.dp)) } }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CompanySelector("Company 1", leftSymbol, available) { symbol -> if (symbol != rightSymbol) leftSymbol = symbol }; CompanySelector("Company 2", rightSymbol, available) { symbol -> if (symbol != leftSymbol) rightSymbol = symbol } } }
        item { if (loading) CompareNote("Loading sourced company data…") else CompareTable(leftStock, rightStock, metrics) }
        if (!loading) {
            item { CompareNote("This is a side-by-side factual comparison of available provider data. It does not rank the companies or give a BUY/SELL instruction.") }
            item { Text("Source dates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CompareText) }
            item { SourcePair(leftStock.name, leftResult.profile.financialProviderUpdatedAt, leftResult.profile.financialPageCheckedAt, rightStock.name, rightResult.profile.financialProviderUpdatedAt, rightResult.profile.financialPageCheckedAt) }
        }
    }
}

@Composable private fun CompanySelector(label: String, selected: String, stocks: List<Stock>, onSelected: (String) -> Unit) {
    var expanded by remember(selected) { mutableStateOf(false) }
    Column(Modifier.width(190.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CompareMuted)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), shape = RoundedCornerShape(10.dp)) { Text(selected, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                stocks.forEach { company -> DropdownMenuItem(text = { Text(company.symbol + " • " + company.name, fontSize = 10.sp) }, onClick = { onSelected(company.symbol); expanded = false }) }
            }
        }
    }
}

@Composable private fun CompareTable(left: Stock, right: Stock, metrics: List<ComparisonMetric>) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CompareBorder)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text("Metric", Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CompareMuted); Text(left.symbol, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = CompareGreen); Text(right.symbol, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = CompareGreen) }
            HorizontalDivider(color = CompareBorder)
            metrics.forEachIndexed { index, metric -> Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(metric.label, Modifier.weight(1f), fontSize = 9.sp, color = CompareText); Text(metric.left, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CompareText); Text(metric.right, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CompareText) }; if (index < metrics.lastIndex) HorizontalDivider(color = CompareBorder) }
        }
    }
}

@Composable private fun SourcePair(leftName: String, leftUpdated: String, leftChecked: String, rightName: String, rightUpdated: String, rightChecked: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CompareLight)) { Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { SourceLine(leftName, leftUpdated, leftChecked); SourceLine(rightName, rightUpdated, rightChecked) } }
}
@Composable private fun SourceLine(name: String, updated: String, checked: String) {
    Column { Text(name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CompareText); Text(buildString { if (updated.isNotBlank()) append("Provider updated " + updated); if (checked.isNotBlank()) { if (isNotEmpty()) append(" • "); append("page checked " + checked) }; if (isEmpty()) append("Provider dates unavailable") }, fontSize = 8.sp, color = CompareMuted) }
}
@Composable private fun CompareNote(text: String) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CompareLight)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = CompareGreen, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 9.sp, lineHeight = 13.sp, color = CompareMuted) } } }
private fun value(raw: String): String = raw.trim().ifBlank { "Unavailable" }
private fun percent(raw: String): String { val v = raw.trim(); if (v.isBlank()) return "Unavailable"; return if (v.contains("%")) v else v + "%" }
private fun financial(raw: String, unit: String): String { val v = raw.trim(); if (v.isBlank()) return "Unavailable"; return if (unit.isBlank()) v else v + " " + unit }
private val CompareGreen = androidx.compose.ui.graphics.Color(0xFF00A859)
private val CompareLight = androidx.compose.ui.graphics.Color(0xFFE9F8F0)
private val CompareText = androidx.compose.ui.graphics.Color(0xFF12231B)
private val CompareMuted = androidx.compose.ui.graphics.Color(0xFF6C7A72)
private val CompareBorder = androidx.compose.ui.graphics.Color(0xFFE1EAE5)