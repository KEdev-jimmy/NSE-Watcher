package ke.co.nsewatcher

import org.junit.Assert.*
import org.junit.Test

class CompaniesPresentationTest {
    private fun stock(symbol: String = "KCB", name: String = "KCB Group", price: Double = 50.0, change: Double = 2.0, sector: String = "Banking") =
        Stock(symbol, name, price, change, emptyList(), sector = sector)

    @Test fun directoryKeepsCatalogueCompaniesWithoutInventingQuotes() {
        val result = CompaniesPresentation.companies(listOf(stock(), stock("SCOM", "Safaricom", sector = "Telecommunications")), listOf(stock(price = 55.0)))
        assertEquals(2, result.size)
        assertEquals(55.0, result.first { it.symbol == "KCB" }.price, 0.0)
        val missing = result.first { it.symbol == "SCOM" }
        assertTrue(missing.price.isNaN())
        assertFalse(missing.changeAvailable)
        assertEquals("Telecom", missing.sector)
    }

    @Test fun identityIsNormalizedAndQuoteOnlyCompaniesRemainDiscoverable() {
        val result = CompaniesPresentation.companies(listOf(stock(" kcb ")), listOf(stock("KCB"), stock("EQTY", "Equity Group")))
        assertEquals(listOf("EQTY", "KCB"), result.map { it.symbol })
    }

    @Test fun searchAndSectorFiltersWorkTogether() {
        val rows = CompaniesPresentation.companies(emptyList(), listOf(stock(), stock("SCOM", "Safaricom", sector = "Telecommunications")))
        assertEquals("SCOM", CompaniesPresentation.visible(rows, " scom ", "Telecom", CompanySort.NAME).single().symbol)
        assertTrue(CompaniesPresentation.visible(rows, "scom", "Banking", CompanySort.NAME).isEmpty())
        assertEquals("KCB", CompaniesPresentation.visible(rows, "group", "All", CompanySort.NAME).single().symbol)
    }

    @Test fun missingNumbersSortLastInBothChangeDirections() {
        val rows = listOf(stock("A", "Alpha", change = -1.0), stock("B", "Beta", change = 5.0), stock("C", "Cee", change = Double.NaN), stock("D", "Dee", change = 10.0).copy(changeAvailable = false))
        assertEquals(listOf("B", "A", "C", "D"), CompaniesPresentation.visible(rows, "", "All", CompanySort.GAIN).map { it.symbol })
        assertEquals(listOf("A", "B", "C", "D"), CompaniesPresentation.visible(rows, "", "All", CompanySort.LOSS).map { it.symbol })
    }

    @Test fun missingPriceIsNotCheapestCompany() {
        val rows = listOf(stock("A", "Alpha", Double.NaN), stock("B", "Beta", 20.0), stock("C", "Cee", 10.0), stock("D", "Dee", 0.0))
        assertEquals(listOf("C", "B", "A", "D"), CompaniesPresentation.visible(rows, "", "All", CompanySort.PRICE).map { it.symbol })
    }

    @Test fun selectionTogglesWithoutExceedingTwoCompanies() {
        assertEquals(listOf("KCB"), CompaniesPresentation.toggleSelection(emptyList(), " kcb "))
        assertEquals(listOf("KCB", "EQTY"), CompaniesPresentation.toggleSelection(listOf("KCB"), "EQTY"))
        assertEquals(listOf("KCB", "EQTY"), CompaniesPresentation.toggleSelection(listOf("KCB", "EQTY"), "SCOM"))
        assertEquals(listOf("EQTY"), CompaniesPresentation.toggleSelection(listOf("KCB", "EQTY"), "KCB"))
    }

    @Test fun newsLinksRequireMatchedActualArticles() {
        fun news(id: String, symbol: String, date: String) = NewsItem(id, "Actual title", "", "", "Publisher", date, "News", symbol, "", "", "", "", "", "")
        val old = news("old", "KCB", "2026-09-20")
        val recent = news("new", "KCB", "2026-09-22")
        val unrelated = news("other", "SCOM", "2026-09-23")
        val links = CompaniesPresentation.newsByCompany(listOf(old, recent, unrelated), listOf(stock(), stock("EQTY")))
        assertEquals(recent, links["KCB"])
        assertFalse(links.containsKey("EQTY"))
    }

    @Test fun sectorAliasesMergeWithoutGuessingUnspecifiedSectors() {
        assertEquals("Banking", CompaniesPresentation.sector("Banks"))
        assertEquals("Telecom", CompaniesPresentation.sector("Telecommunication"))
        assertEquals("Other", CompaniesPresentation.sector(""))
        assertEquals("Agriculture", CompaniesPresentation.sector("Agriculture"))
    }
}
