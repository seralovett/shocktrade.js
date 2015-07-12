package com.shocktrade.javascript.discover

import com.shocktrade.javascript.{ScalaJsHelper, GlobalLoading, MainController}
import ScalaJsHelper._
import com.github.ldaniels528.scalascript.core.Timeout
import com.github.ldaniels528.scalascript.extensions.{Cookies, Toaster}
import com.github.ldaniels528.scalascript.{Controller, angular, injected}
import com.shocktrade.javascript.discover.ResearchController._
import com.shocktrade.javascript.{GlobalLoading, MainController}
import org.scalajs.dom.console

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g, literal => JS}
import scala.util.{Failure, Success}

/**
 * Research Controller
 * @author lawrence.daniels@gmail.com
 */
class ResearchController($scope: js.Dynamic, $cookies: Cookies, $timeout: Timeout, toaster: Toaster,
                         @injected("ResearchService") researchService: ResearchService)
  extends Controller with GlobalLoading {

  // search reference data components
  private var exchangeCounts = js.Dictionary[Int]()
  private var filteredResults = emptyArray[js.Dynamic]
  private var searchResults = emptyArray[js.Dynamic]

  //////////////////////////////////////////////////////////////////////
  //              Public Data
  //////////////////////////////////////////////////////////////////////

  $scope.exchangeSets = js.Dictionary[Boolean](
    "AMEX" -> true,
    "NASDAQ" -> true,
    "NYSE" -> true,
    "OTCBB" -> true,
    "OTHER_OTC" -> true
  )

  $scope.maxResultsSet = MaxResultsSet

  $scope.priceRanges = PriceRanges

  $scope.volumeRanges = VolumeRanges

  $scope.percentages = Percentages

  $scope.changePercentages = ChangePercentages

  //////////////////////////////////////////////////////////////////////
  //              Public Functions
  //////////////////////////////////////////////////////////////////////

  $scope.getFilteredResults = () => filteredResults

  $scope.getSearchResults = () => searchResults

  $scope.getExchangeCount = (exchange: js.UndefOr[String]) => getExchangeCount(exchange)

  $scope.searchOptions = JS(
    sortBy = null,
    reverse = false,
    maxResults = MaxResultsSet(1)
  )

  $scope.getExchangeSet = (exchange: js.UndefOr[String]) => getExchangeSet(exchange)

  $scope.filterExchanges = () => filterExchanges()

  $scope.getSearchResults = () => filteredResults

  $scope.getSearchResultClass = (count: js.UndefOr[Int]) => getSearchResultClass(count)

  $scope.getSearchResultsCount = () => filteredResults.length

  $scope.columnAlign = (column: String) => columnAlign(column)

  $scope.rowClass = (column: String, row: js.Dynamic) => rowClass(column, row)

  $scope.quoteSearch = (searchOptions: js.Dynamic) => quoteSearch(searchOptions)

  //////////////////////////////////////////////////////////////////////
  //              Private Functions
  //////////////////////////////////////////////////////////////////////

  private def exchangeSets = $scope.exchangeSets.as[js.Dictionary[Boolean]]

  private def filterExchanges() {
    syncLoading($scope, $timeout) {
      filteredResults = searchResults.filter { q =>
        val exchange = MainController.normalizeExchange(q.exchange.as[String])
        exchangeSets.getOrElse(exchange, false)
      }
    }
  }

  private def getExchangeSet(exchange: js.UndefOr[String]): Boolean = exchange.map(e => exchangeSets.getOrElse(e, false)) getOrElse false

  private def getExchangeCount(exchange: js.UndefOr[String]): Double = (exchange.map(e => exchangeCounts.getOrElse(e, 0)) getOrElse 0).toDouble

  private def getSearchResultClass(count: js.UndefOr[Int]) = {
    count.map {
      case n if n < 250 => "results_small"
      case n if n < 350 => "results_medium"
      case _ => "results_large"
    } getOrElse "results_none"
  }

  private def columnAlign(column: String) = if (column == "symbol") "left" else "right"

  private def quoteSearch(searchOptions: js.Dynamic) = {
    filteredResults = emptyArray
    searchResults = emptyArray

    // execute the search
    asyncLoading($scope)(researchService.search(searchOptions)) onComplete {
      case Success(results) =>
        val exchanges = js.Dictionary[Int]()
        results.foreach { q =>
          // normalize the exchange
          val exchange = MainController.normalizeExchange(q.exchange.as[String])
          q.market = q.exchange
          q.exchange = exchange

          // count the quotes by exchange
          if (!exchanges.contains(exchange)) exchanges(exchange) = 1 else exchanges(exchange) = exchanges(exchange) + 1

          // add missing exchanges to our set
          if (!exchangeSets.contains(exchange)) {
            exchangeSets(exchange) = true
          }
        }

        // update the exchange counts
        exchangeCounts = exchanges
        searchResults = results
        $scope.filterExchanges()

        // save the search options
        $cookies.putObject(CookieName, searchOptions)

      case Failure(e) =>
        g.console.error(s"Quote Search Failed - json => ${angular.toJson(searchOptions, pretty = false)}")
        toaster.error("Failed to execute search")
    }
  }

  private def rowClass(column: String, row: js.Dynamic) = if (column == "symbol") row.exchange else column

  ///////////////////////////////////////////////////////////////////////////
  //          Initialization
  ///////////////////////////////////////////////////////////////////////////

  // retrieve the search options cookie
  $cookies.getObject[js.Dynamic](CookieName) foreach { options =>
    if (isDefined(options)) {
      console.log(s"Retrieved search options from cookie '$CookieName': ${angular.toJson(options, pretty = false)}")
      $scope.searchOptions = options
    }
  }

}

/**
 * Research Controller Singleton
 * @author lawrence.daniels@gmail.com
 */
object ResearchController {
  private val CookieName = "ShockTrade_Research_SearchOptions"

  // data collections
  private val MaxResultsSet = js.Array(10, 25, 50, 75, 100, 150, 200, 250)
  private val PriceRanges = js.Array(0, 1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)
  private val VolumeRanges = js.Array(0, 1000, 5000, 10000, 20000, 50000, 100000, 250000, 500000, 1000000, 5000000, 10000000, 20000000, 50000000)
  private val Percentages = js.Array(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100)
  private val ChangePercentages = js.Array(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, -5, -10, -15, -25, -50, -75, -100)

}
