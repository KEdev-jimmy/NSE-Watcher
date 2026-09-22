package ke.co.nsewatcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.SavedNewsStore
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun NewsArticleScreen(item: NewsItem, catalog: List<Stock>, quotes: List<Stock>, back: () -> Unit, openCompany: (Stock) -> Unit) {
    var current by remember(item.id) { mutableStateOf(item) }
    var trail by remember(item.id) { mutableStateOf(emptyList<NewsItem>()) }
    fun previous() { if (trail.isEmpty()) back() else { current = trail.last(); trail = trail.dropLast(1) } }
    BackHandler(trail.isNotEmpty()) { previous() }
    key(current.id) {
        ArticleReader(current, catalog, quotes, ::previous, openCompany) { next ->
            if (next.id != current.id) { trail = trail + current; current = next }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleReader(item: NewsItem, catalog: List<Stock>, quotes: List<Stock>, back: () -> Unit,
    openCompany: (Stock) -> Unit, openArticle: (NewsItem) -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val bookmarks = remember { SavedNewsStore(context) }
    val watchlist = remember { WatchlistStore(context) }
    val snackbar = remember { SnackbarHostState() }
    var savedError by remember { mutableStateOf(false) }
    var watchError by remember { mutableStateOf(false) }
    val savedFlow = remember { bookmarks.articles.catch { savedError = true } }
    val watchFlow = remember { watchlist.symbols.catch { watchError = true } }
    val sizeFlow = remember { bookmarks.readerSize.catch { savedError = true } }
    val saved by savedFlow.collectAsState<List<NewsItem>, List<NewsItem>?>(null)
    val watched by watchFlow.collectAsState<List<String>, List<String>?>(null)
    val textSize by sizeFlow.collectAsState(initial = 16)
    var detail by remember { mutableStateOf(item) }
    var loading by remember { mutableStateOf(true) }
    var detailError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var moreNews by rememberSaveable { mutableStateOf(false) }
    var localCatalog by remember(catalog) { mutableStateOf(catalog) }
    var imageFailed by remember(detail.imageUrl) { mutableStateOf(false) }
    val isSaved = saved.orEmpty().any { it.id == item.id }
    val universe = remember(localCatalog, quotes) { CompaniesPresentation.companies(localCatalog, quotes) }
    val company = remember(detail, universe) { ArticlePresentation.company(detail, universe) }
    val originalUrl = CompanyResearchPresentation.sourceUrl(detail.url)
    val points = remember(detail) { ArticlePresentation.keyPoints(detail) }
    val hasBody = remember(detail) { ArticlePresentation.hasDistinctBody(detail) }
    val blocks = remember(detail, hasBody) { ArticlePresentation.blocks(if (hasBody) detail.body else detail.summary.ifBlank { detail.body }) }
    val linkColor = Color(0xFF8CCBFF)

    LaunchedEffect(Unit) { if (localCatalog.isEmpty()) localCatalog = MyStocksCache.loadCompanies() }
    LaunchedEffect(retry) {
        loading = true
        try {
            val fresh = NewsCache.loadDetail(item.id)
            detailError = fresh == null || fresh.id != item.id
            if (fresh != null && fresh.id == item.id) {
                detail = ArticlePresentation.merge(detail, fresh)
                try { bookmarks.updateIfSaved(detail) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { savedError = true }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { detailError = true }
        finally { loading = false }
    }
    fun original() {
        originalUrl?.let { link -> runCatching { uri.openUri(link) }.onFailure { scope.launch { snackbar.showSnackbar("No app could open the original link.") } } }
    }
    fun bookmark() {
        if (saving || saved == null || savedError) return
        saving = true
        scope.launch {
            try {
                if (isSaved) bookmarks.remove(detail.id) else bookmarks.save(detail)
                snackbar.currentSnackbarData?.dismiss()
                saving = false
                snackbar.showSnackbar(if (isSaved) "Article removed from Saved" else "Article saved. Find it in News → Saved.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("Could not update saved articles. Please try again.") }
            finally { saving = false }
        }
    }
    fun share() {
        val content = listOfNotNull(ArticlePresentation.text(detail.title), detail.source.takeIf { it.isNotBlank() }, originalUrl).joinToString("\n")
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content) }, "Share article")) }
            .onFailure { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Scaffold(containerColor = ResearchBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back", tint = ResearchText) }
                        Text("News", Modifier.weight(1f), color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = ::bookmark, enabled = saved != null && !saving && !savedError) {
                            Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (isSaved) "Remove saved article" else "Save article", tint = if (isSaved) ResearchGreen else ResearchText)
                        }
                        IconButton(onClick = ::share) { Icon(Icons.Default.Share, "Share article", tint = ResearchText) }
                        Box {
                            TextButton(onClick = { sizeMenu = true }, modifier = Modifier.semantics { contentDescription = "Adjust reading text size" }) { Text("Aa", color = ResearchText, fontSize = 18.sp) }
                            DropdownMenu(sizeMenu, { sizeMenu = false }) {
                                listOf(16 to "Standard", 18 to "Comfortable", 20 to "Large", 22 to "Extra large").forEach { (size, label) ->
                                    DropdownMenuItem(text = { Text("$label${if (textSize == size) " ✓" else ""}") }, onClick = {
                                        sizeMenu = false
                                        scope.launch {
                                            try { bookmarks.setReaderSize(size) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { snackbar.showSnackbar("Could not save reading size.") }
                                        }
                                    })
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = ResearchBorder)
                }
            }, bottomBar = {
                Surface(color = ResearchBackground, border = BorderStroke(1.dp, ResearchBorder)) {
                    OutlinedButton(onClick = ::original, enabled = originalUrl != null, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (originalUrl != null) ResearchGreen else ResearchBorder)) {
                        Text(if (originalUrl != null) "Read original article ↗" else "Original link unavailable", fontWeight = FontWeight.SemiBold)
                    }
                }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Surface(color = ResearchRaised, shape = RoundedCornerShape(20.dp)) { Text(detail.category.ifBlank { "News" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = ResearchMuted, fontSize = 12.sp) }
                    Spacer(Modifier.height(12.dp))
                    Text(ArticlePresentation.text(detail.title), color = ResearchText, fontSize = (textSize + 10).sp, lineHeight = (textSize + 17).sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = ::original, enabled = originalUrl != null, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(17.dp), tint = ResearchMuted); Spacer(Modifier.width(7.dp))
                        Text(detail.source.ifBlank { "Source unavailable" } + if (originalUrl != null) " ↗" else "", color = if (originalUrl != null) linkColor else ResearchMuted, fontSize = 13.sp)
                    }
                    ResearchCaption("Published · ${CompanyResearchPresentation.date(detail.publishedAt)}")
                    company?.let { stock ->
                        OutlinedButton(onClick = { openCompany(stock) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(20.dp)) {
                            Text("${stock.symbol} · ${stock.name} ↗", color = linkColor, fontSize = 12.sp)
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = ResearchGreen) }
                if (detailError) item {
                    ResearchCaption("Could not refresh the article. Available text remains below.")
                    TextButton(onClick = { retry++ }, enabled = !loading) { Text("Retry article", color = ResearchGreen) }
                }
                if (savedError) item { ResearchCaption("Saved articles could not be accessed. Reopen this page to retry.") }
                if (detail.imageUrl.isNotBlank() && !imageFailed) item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        AsyncImage(model = detail.imageUrl, contentDescription = "Image supplied with this article", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), onError = { imageFailed = true })
                        ResearchCaption("Image supplied with article")
                    }
                }
                if (points.isNotEmpty()) item {
                    ResearchPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = ResearchMuted)
                            Column { ResearchTitle("Key points"); ResearchCaption("Excerpts from the supplied summary") }
                        }
                        points.forEach { point ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("•", color = ResearchGreen, fontSize = textSize.sp)
                                SelectionContainer { Text(point, color = ResearchText, fontSize = textSize.sp, lineHeight = (textSize + 8).sp) }
                            }
                        }
                    }
                }
                if (!hasBody) item {
                    ResearchPanel {
                        ResearchTitle(if (blocks.isEmpty()) "Article text unavailable" else "Summary only")
                        ResearchCaption(if (originalUrl != null) "The feed has not supplied a separate article body. Open the original source for the complete story." else "The feed has not supplied a separate article body or a usable original link.")
                    }
                } else item { ResearchCaption("Article text supplied by the source") }
                items(blocks.size) { index ->
                    val block = blocks[index]
                    SelectionContainer {
                        Text(block.text, color = ResearchText, fontSize = (textSize + if (block.heading) 3 else 0).sp,
                            lineHeight = (textSize + 9).sp, fontWeight = if (block.heading) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (detail.dividendAmount.isNotBlank() || detail.exDate.isNotBlank() || detail.paymentDate.isNotBlank()) item {
                    ResearchPanel {
                        ResearchTitle("Dividend details supplied")
                        if (detail.dividendAmount.isNotBlank()) ResearchBody("Amount: ${detail.dividendAmount}")
                        if (detail.exDate.isNotBlank()) ResearchBody("Ex-date / book closure: ${detail.exDate}")
                        if (detail.paymentDate.isNotBlank()) ResearchBody("Payment date: ${detail.paymentDate}")
                        ResearchCaption("Check the original announcement for approval requirements and final dates.")
                    }
                }
                item {
                    ResearchPanel {
                        ResearchTitle("About this article")
                        TextButton(onClick = ::original, enabled = originalUrl != null, contentPadding = PaddingValues(0.dp)) { Text("Source: ${detail.source.ifBlank { "Unavailable" }}${if (originalUrl != null) " ↗" else ""}", color = linkColor) }
                        ResearchCaption("Published: ${CompanyResearchPresentation.date(detail.publishedAt)}")
                        ResearchCaption(if (points.isNotEmpty()) "Key points are excerpts from the source-supplied summary. They are not investment advice." else "Source content is provided for research, not as a recommendation to buy or sell.")
                    }
                }
                company?.let { stock ->
                    item {
                        val isWatched = watched.orEmpty().any { it.equals(stock.symbol, true) }
                        ResearchPanel {
                            ResearchTitle("Explore the company")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) { Text(stock.symbol.take(1), color = ResearchGreen, fontSize = 22.sp) }
                                Column(Modifier.weight(1f)) { ResearchBody(stock.name); ResearchCaption("Financials, performance and sourced research") }
                            }
                            Button(onClick = { openCompany(stock) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Open Company Intelligence →") }
                            OutlinedButton(onClick = {
                                adding = true
                                scope.launch {
                                    try { watchlist.add(stock.symbol) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { snackbar.showSnackbar("Could not add this company. Please try again.") }
                                    finally { adding = false }
                                }
                            }, enabled = watched != null && !watchError && !isWatched && !adding, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                Icon(if (isWatched) Icons.Default.Star else Icons.Default.StarBorder, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (isWatched) "In your watchlist" else if (adding) "Saving…" else "Add to watchlist")
                            }
                            if (watchError) ResearchCaption("Watchlist is unavailable. Reopen this page to retry.")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { moreNews = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                            Icon(Icons.Default.Description, null, tint = ResearchMuted); Spacer(Modifier.width(10.dp)); Text("More news about ${stock.symbol}", Modifier.weight(1f), color = ResearchText); Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted)
                        }
                    }
                }
                if (company == null && (detail.symbol.isNotBlank() || detail.companyName.isNotBlank())) item { ResearchCaption("A matching company profile is not currently available for this article.") }
            }
        }
        if (moreNews && company != null) RelatedArticleSheet(company, detail.id, { moreNews = false }) { next -> moreNews = false; openArticle(next) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedArticleSheet(company: Stock, currentId: String, dismiss: () -> Unit, open: (NewsItem) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var stories by remember { mutableStateOf(emptyList<NewsItem>()) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true
        try {
            val direct = NewsCache.loadCompanyNews(company.symbol)
            val feed = NewsCache.loadFeedResult(forceRefresh = retry > 0)
            error = direct.error != null && feed.error != null
            if (!error) stories = WatchlistPresentation.linkedNews(direct.items + feed.items, listOf(company)).filter { it.id != currentId }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                ResearchTitle("More news about ${company.symbol}")
                TextButton(onClick = { retry++ }, enabled = !loading) { Text("Refresh", color = ResearchGreen) }
                if (loading) ResearchLoading("Loading company news…")
                else if (error) ResearchCaption("Company news could not be updated. Please retry.")
                else if (stories.isEmpty()) ResearchCaption("No other matching articles were returned by the available feeds.")
            }
            items(stories, key = { it.id }) { story ->
                Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { open(story) }, color = ResearchCard, border = BorderStroke(1.dp, ResearchBorder), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResearchBody(ArticlePresentation.text(story.title))
                        ResearchCaption("${story.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(story.publishedAt)}")
                        Text("Read article →", color = ResearchGreen)
                    }
                }
            }
        }
    }
}
