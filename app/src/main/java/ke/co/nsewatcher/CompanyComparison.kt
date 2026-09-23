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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException

private data class ComparisonMetric(val label: String, val left: String, val right: String)

@Composable
fun CompanyComparison(stocks: List<Stock>, back: () -> Unit, initialSymbols: List<String> = emptyList()) {
    val available = stocks.distinctBy { it.symbol }.filter { it.symbol.isNotBlank() }
    if (available.size < 2) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column { Text("Compare companies", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Side-by-side sourced company data", fontSize = 13.sp, color = CompareMuted) }
                }
            }
            item { CompareNote("At least two companies are needed for a comparison.") }
        }
        return
    }
    var leftSymbol by rememberSaveable(initialSymbols) { mutableStateOf(initialSymbols.firstOrNull { symbol -> available.any { it.symbol == symbol } } ?: available[0].symbol) }
    var rightSymbol by rememberSaveable(initialSymbols) { mutableStateOf(initialSymbols.firstOrNull { symbol -> symbol != leftSymbol && available.any { it.symbol == symbol } } ?: available.first { it.symbol != leftSymbol }.symbol) }
    val leftStock = available.firstOrNull { it.symbol == leftSymbol } ?: available[0]
    val rightStock = available.firstOrNull { it.symbol == rightSymbol && it.symbol != leftStock.symbol } ?: available.first { it.symbol != leftStock.symbol }
    var leftResult by remember(leftStock.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var rightResult by remember(rightStock.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var loading by remember(leftStock.symbol, rightStock.symbol) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf(false) }
    LaunchedEffect(leftStock.symbol, rightStock.symbol, retry) {
        loading = true
        try {
            coroutineScope {
                val results = listOf(async { CompanyIntelligenceCache.load(leftStock.symbol) }, async { CompanyIntelligenceCache.load(rightStock.symbol) }).awaitAll()
                leftResult = results[0]; rightResult = results[1]
            }
            loadError = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
        finally { loading = false }
    }
    val metrics = listOf(
        ComparisonMetric("Price", CompanyResearchPresentation.money(leftStock.price), CompanyResearchPresentation.money(rightStock.price)),
        ComparisonMetric("Sector", leftStock.sector, rightStock.sector),
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
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; Column(Modifier.weight(1f)) { Text("Compare companies", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Compare sourced figures side by side", fontSize = 13.sp, color = CompareMuted) }; Icon(Icons.Default.CompareArrows, null, tint = CompareGreen, modifier = Modifier.size(24.dp)) } }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CompanySelector("Company 1", leftStock.symbol, available) { symbol -> if (symbol != rightStock.symbol) leftSymbol = symbol }; CompanySelector("Company 2", rightStock.symbol, available) { symbol -> if (symbol != leftStock.symbol) rightSymbol = symbol } } }
        item { if (loading) CompareNote("Loading sourced company data…") else CompareTable(leftStock, rightStock, metrics) }
        if (!loading) item { CompareNote("Prices use each company’s latest available NSE observation (" + observationTime(leftStock) + "; " + observationTime(rightStock) + "). The feed is provider-supplied and 15-minute delayed.") }
        if (!loading) {
            if (loadError) item { CompareNote("Company research could not be refreshed. Available values remain shown.") }
            if (leftStock.sector != rightStock.sector) item { CompareNote("These companies are in different sectors. Their financial ratios may not be directly comparable.") }
            item { CompareNote("This is a side-by-side factual comparison of available provider data. It does not rank the companies or give a BUY/SELL instruction.") }
            item { Text("Source dates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CompareText) }
            item { SourcePair(leftStock.name, leftResult.profile.financialProviderUpdatedAt, leftResult.profile.financialPageCheckedAt, rightStock.name, rightResult.profile.financialProviderUpdatedAt, rightResult.profile.financialPageCheckedAt) }
            item { TextButton(onClick = { retry++ }) { Text("Refresh company research", color = CompareGreen) } }
        }
    }
}

@Composable private fun CompanySelector(label: String, selected: String, stocks: List<Stock>, onSelected: (String) -> Unit) {
    var expanded by remember(selected) { mutableStateOf(false) }
    Column(Modifier.width(190.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CompareMuted)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), shape = RoundedCornerShape(10.dp)) { Text(selected, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                stocks.forEach { company -> DropdownMenuItem(text = { Text(company.symbol + " • " + company.name, fontSize = 13.sp) }, onClick = { onSelected(company.symbol); expanded = false }) }
            }
        }
    }
}

@Composable private fun CompareTable(left: Stock, right: Stock, metrics: List<ComparisonMetric>) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CompareBorder), colors = CardDefaults.cardColors(containerColor = ResearchCard)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text("Metric", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CompareMuted); Text(left.symbol, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = CompareGreen); Text(right.symbol, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = CompareGreen) }
            HorizontalDivider(color = CompareBorder)
            metrics.forEachIndexed { index, metric -> Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(metric.label, Modifier.weight(1f), fontSize = 12.sp, color = CompareText); Text(metric.left, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CompareText); Text(metric.right, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CompareText) }; if (index < metrics.lastIndex) HorizontalDivider(color = CompareBorder) }
        }
    }
}

@Composable private fun SourcePair(leftName: String, leftUpdated: String, leftChecked: String, rightName: String, rightUpdated: String, rightChecked: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CompareLight)) { Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { SourceLine(leftName, leftUpdated, leftChecked); SourceLine(rightName, rightUpdated, rightChecked) } }
}
@Composable private fun SourceLine(name: String, updated: String, checked: String) {
    Column { Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CompareText); Text(buildString { if (updated.isNotBlank()) append("Provider updated " + updated); if (checked.isNotBlank()) { if (isNotEmpty()) append(" • "); append("page checked " + checked) }; if (isEmpty()) append("Provider dates unavailable") }, fontSize = 11.sp, color = CompareMuted) }
}
@Composable private fun CompareNote(text: String) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CompareLight)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = CompareGreen, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = CompareMuted) } } }
private fun observationTime(stock: Stock): String {
    if (stock.observedAt.isBlank()) return stock.symbol + ": time unavailable"
    val observed = runCatching {
        java.time.Instant.parse(stock.observedAt)
            .atZone(java.time.ZoneId.of("Africa/Nairobi"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm", java.util.Locale.US))
    }.getOrDefault(stock.observedAt.replace("T", " ").removeSuffix("Z").take(16))
    return stock.symbol + " as of " + observed + " EAT"
}
private fun value(raw: String): String = raw.trim().ifBlank { "Unavailable" }
private fun percent(raw: String): String { val v = raw.trim(); if (v.isBlank()) return "Unavailable"; return if (v.contains("%")) v else v + "%" }
private fun financial(raw: String, unit: String): String { val v = raw.trim(); if (v.isBlank()) return "Unavailable"; return if (unit.isBlank()) v else v + " " + unit }
private val CompareGreen: Color
    @Composable get() = ResearchGreen
private val CompareLight: Color
    @Composable get() = ResearchCard
private val CompareText: Color
    @Composable get() = ResearchText
private val CompareMuted: Color
    @Composable get() = ResearchMuted
private val CompareBorder: Color
    @Composable get() = ResearchBorder
