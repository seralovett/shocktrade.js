package com.shocktrade.client.contest

import com.shocktrade.client.MySessionService
import com.shocktrade.client.ScopeEvents._
import com.shocktrade.client.dialogs.NewGameDialog
import com.shocktrade.client.models.contest.Contest
import com.shocktrade.common.models.contest.Participant
import io.scalajs.npm.angularjs.AngularJsHelper._
import io.scalajs.npm.angularjs.toaster.Toaster
import io.scalajs.npm.angularjs.{Location, Timeout, injected}
import io.scalajs.dom.html.browser.console
import io.scalajs.util.PromiseHelper.Implicits._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

/**
  * My Games Controller
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
class MyGamesController($scope: MyGamesScope, $location: Location, $timeout: Timeout, toaster: Toaster,
                        @injected("ContestService") contestService: ContestService,
                        @injected("MySessionService") mySession: MySessionService,
                        @injected("NewGameDialog") newGameDialog: NewGameDialog,
                        @injected("PortfolioService") portfolioService: PortfolioService)
  extends GameController($scope, $location, toaster, mySession, portfolioService) {

  private var myContests = js.Array[Contest]()

  $scope.includeExpiry = false

  ///////////////////////////////////////////////////////////////////////////
  //          Scope Functions
  ///////////////////////////////////////////////////////////////////////////

  $scope.initMyGames = () => reload()

  $scope.getMyContests = () => myContests

  $scope.getMyRankings = (aContest: js.UndefOr[Contest]) => aContest flatMap { contest =>
    mySession.updateRankings(contest).player
  }

  $scope.popupNewGameDialog = () => {
    newGameDialog.popup() onComplete {
      case Success(contest) =>
        if (contest.error.nonEmpty) toaster.error(contest.error.getOrElse("General Fault"))
        else {
          // TODO add to Contests
          $scope.$apply(() => reload())
        }

      case Failure(e) =>
        toaster.error("Failed to create game")
        console.error(s"Failed to create game ${e.displayMessage}")
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Private Methods
  ///////////////////////////////////////////////////////////////////////////

  private def reload(): Unit = mySession.userProfile._id foreach loadMyContests

  private def loadMyContests(userID: String) {
    console.log(s"Loading 'My Contests' for user '$userID'...")
    contestService.getContestsByPlayerID(userID) onComplete {
      case Success(response) =>
        val contests = response.data
        console.log(s"Loaded ${contests.length} contest(s)")
        $scope.$apply(() => myContests = contests)
      case Failure(e) =>
        toaster.error("Failed to load 'My Contests'")
        console.error(s"Failed to load 'My Contests': ${e.displayMessage}")
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  //          Event Listeners
  ///////////////////////////////////////////////////////////////////////////

  /**
    * Listen for contest creation events
    */
  $scope.onContestCreated((_, contest) => reload())

  /**
    * Listen for contest deletion events
    */
  $scope.onContestDeleted((_, contest) => reload())

  /**
    * Listen for user profile changes
    */
  $scope.onUserProfileChanged((_, profile) => reload())

}

/**
  * My Games Controller Scope
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@js.native
trait MyGamesScope extends GameScope {
  var includeExpiry: Boolean = js.native

  // functions
  var initMyGames: js.Function0[Unit] = js.native
  var getMyContests: js.Function0[js.Array[Contest]] = js.native
  var getMyRankings: js.Function1[js.UndefOr[Contest], js.UndefOr[Participant]] = js.native
  var popupNewGameDialog: js.Function0[Unit] = js.native

}
