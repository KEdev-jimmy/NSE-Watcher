package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MarketObservationStore
import ke.co.nsewatcher.data.MovementIntelligenceCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDashboard(
    stockFeed: List<Stock>,
    catalog: List<Stock>,
    initialStatus: MyStocksCache.MarketStatus,
    marketIndices: List<MyStocksCache.MarketIndex>,
    newsFeed: List<NewsItem>,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    openAlerts: () -> Unit = {},
    onQuotesLoaded: (List<Stock>) -> Unit,
    onCatalogLoaded: (List<Stock>) -> Unit,
    onIndicesLoaded: (List<MyStocksCache.MarketIndex>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit
) {
    PremiumMarketExperience(
        stockFeed = stockFeed,
        catalog = catalog,
        initialStatus = initialStatus,
        marketIndices = marketIndices,
        newsFeed = newsFeed,
        openCompany = openCompany,
        openCompanies = openCompanies,
        openAlerts = openAlerts,
        onQuotesLoaded = onQuotesLoaded,
        onCatalogLoaded = onCatalogLoaded,
        onIndicesLoaded = onIndicesLoaded,
        onMarketStatusLoaded = onMarketStatusLoaded
    )
}

private fun marketMovers(companies: List<Stock>, type: String): List<Stock> = when (type) {
    "By volume" -> companies.filter { it.volumeAvailable && it.volume > 0 }.sortedByDescending { it.volume }
    "Losers" -> companies.filter { MarketPresentation.validChange(it) && it.change < 0 }.sortedBy { it.change }
    else -> companies.filter { MarketPresentation.validChange(it) && it.change > 0 }.sortedByDescending { it.change }
}

@Composable
private fun MarketWhyMovingAction(stock: Stock, explain: (Stock) -> Unit) {
    TextButton(
        onClick = { explain(stock) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Outlined.Insights, null, tint = ResearchGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Why is ${stock.symbol} moving?", color = ResearchGreen, fontSize = 12.sp)
    }
}

@Composable
internal fun MarketChoiceRow(options: List<String>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) { options.forEach { option ->
        Column(Modifier.weight(1f)) {
            TextButton(onClick = { select(option) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) { Text(option, color = if (option == selected) ResearchGreen else ResearchMuted, fontSize = 12.sp, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal) }
            if (option == selected) Box(Modifier.fillMaxWidth().height(2.dp).background(ResearchGreen))
        }
    } }
}

@Composable
internal fun MarketBreadthBar(breadth: MarketBreadth) {
    if (breadth.covered > 0) Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp))) {
        if (breadth.rising > 0) Box(Modifier.weight(breadth.rising.toFloat()).fillMaxHeight().background(ResearchGreen))
        if (breadth.flat > 0) Box(Modifier.weight(breadth.flat.toFloat()).fillMaxHeight().background(ResearchMuted))
        if (breadth.falling > 0) Box(Modifier.weight(breadth.falling.toFloat()).fillMaxHeight().background(ResearchRed))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${breadth.rising} rising", color = ResearchGreen, fontSize = 11.sp)
        Text("${breadth.flat} flat", color = ResearchMuted, fontSize = 11.sp)
        Text("${breadth.falling} falling", color = ResearchRed, fontSize = 11.sp)
    }
}

@Composable
internal fun MarketStockRow(stock: Stock, value: String, change: Double?, open: (Stock) -> Unit, dates: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Research ${stock.name}") { open(stock) }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) { Text(stock.symbol.take(1), color = ResearchGreen, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) { Text(stock.name, color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); ResearchCaption(stock.symbol) }
            Text(value, color = researchChangeColor(change), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 125.dp))
            Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(16.dp))
        }
        Text(dates ?: "Observed: ${CompanyResearchPresentation.date(stock.observedAt)}", Modifier.padding(start = 40.dp, top = 4.dp), color = ResearchMuted, fontSize = 10.sp)
    }
}


// Sector identity stays mint regardless of daily return; red/green values carry performance.
@Composable
private fun MarketSectorIcon(sector: String) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(ResearchGreen.copy(alpha = 0.18f), ResearchGreen.copy(alpha = 0.04f)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(marketSectorSymbol(sector), contentDescription = null,
                tint = ResearchGreen.copy(alpha = 0.75f), modifier = Modifier.size(27.dp))
        }
    }
}

private fun marketSectorSymbol(sector: String): ImageVector {
    val name = sector.trim().lowercase(Locale.US)
    return when {
        name.contains("bank") -> Icons.Outlined.AccountBalance
        name.contains("telecom") -> Icons.Outlined.CellTower
        name.contains("agric") || name.contains("farm") -> Icons.Outlined.Eco
        name.contains("energy") || name.contains("petroleum") || name.contains("oil") || name.contains("gas") -> Icons.Outlined.Bolt
        name.contains("insurance") -> Icons.Outlined.Shield
        name.contains("construct") || name.contains("allied") -> Icons.Outlined.Construction
        name.contains("manufactur") -> Icons.Outlined.Factory
        name.contains("automobil") || name.contains("transport") -> Icons.Outlined.DirectionsCar
        name.contains("real estate") || name.contains("reit") || name.contains("property") -> Icons.Outlined.Apartment
        name.contains("invest") || name.contains("exchange traded") || name.contains("etf") -> Icons.Outlined.PieChart
        name.contains("commercial") || name.contains("service") -> Icons.Outlined.Storefront
        else -> Icons.Outlined.Category
    }
}
