package com.shocktrade.client

import io.scalajs.util.DurationHelper._
import com.shocktrade.client.ScopeEvents._
import com.shocktrade.client.contest.{ChatService, ContestService, PortfolioService}
import com.shocktrade.client.models.UserProfile
import com.shocktrade.client.models.contest._
import com.shocktrade.client.profile.UserProfileService
import com.shocktrade.common.models.contest.{ChatMessage, Participant}
import io.scalajs.dom.html.browser.console
import io.scalajs.npm.angularjs.AngularJsHelper._
import io.scalajs.npm.angularjs._
import io.scalajs.npm.angularjs.facebook.FacebookService
import io.scalajs.npm.angularjs.toaster.Toaster
import io.scalajs.social.facebook.{FacebookProfileResponse, TaggableFriend}
import io.scalajs.util.JsUnderOrHelper._
import io.scalajs.util.PromiseHelper.Implicits._
import io.scalajs.util.ScalaJsHelper._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => JS}
import scala.scalajs.js.JSConverters._
import scala.util.{Failure, Success}

/**
  * My Session Service
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
case class MySessionService($rootScope: Scope, $timeout: Timeout, toaster: Toaster,
                            @injected("Facebook") facebook: FacebookService,
                            @injected("ChatService") chatService: ChatService,
                            @injected("ContestService") contestService: ContestService,
                            @injected("PortfolioService") portfolioService: PortfolioService,
                            @injected("UserProfileService") profileService: UserProfileService,
                            @injected("QuoteCache") quoteCache: QuoteCache)
  extends Service {

  private val notifications = emptyArray[String]
  var facebookID: js.UndefOr[String] = js.undefined
  var fbFriends_? : js.Array[TaggableFriend] = emptyArray[TaggableFriend]
  var fbProfile_? : js.UndefOr[FacebookProfileResponse] = js.undefined

  var contest_? : Option[Contest] = None
  var portfolio_? : Option[Portfolio] = None
  var participant_? : Option[Participant] = None
  var userProfile: UserProfile = createSpectatorProfile()

  /////////////////////////////////////////////////////////////////////
  //          Authentication & Authorization Functions
  /////////////////////////////////////////////////////////////////////

  def setUserProfile(profile: UserProfile, profileFB: FacebookProfileResponse) {
    this.userProfile = profile
    this.fbProfile_? = profileFB
    this.facebookID = fbProfile_?.map(_.id)

    // broadcast the user profile change event
    $rootScope.emitUserProfileChanged(profile)
    ()
  }

  /**
    * Returns the user ID for the current user's name
    * @return {*}
    */
  def getUserName: js.UndefOr[String] = userProfile.name

  /**
    * Indicates whether the given user is an administrator
    * @return {boolean}
    */
  def isAdmin: Boolean = userProfile.isAdmin.isTrue

  /**
    * Indicates whether the user is logged in
    * @return {boolean}
    */
  def isAuthenticated: Boolean = userProfile._id.isAssigned

  def getFacebookID: js.UndefOr[String] = facebookID

  def setFacebookID(fbId: String): Unit = facebookID = fbId

  def getFacebookProfile: js.UndefOr[FacebookProfileResponse] = fbProfile_?

  def setFacebookProfile(profile: FacebookProfileResponse): Unit = fbProfile_? = profile

  def isFbAuthenticated: Boolean = fbProfile_?.isDefined

  /**
    * Logout function
    */
  def logout(): Unit = {
    facebookID = js.undefined
    fbFriends_? = js.Array[TaggableFriend]()
    fbProfile_? = js.undefined
    userProfile = createSpectatorProfile()
    resetContest()
  }

  def refresh(): Unit = {
    facebookID.foreach { fbId =>
      profileService.getProfileByFacebookID(fbId) onComplete {
        case Success(response) =>
          $rootScope.$apply { () =>
            userProfile.netWorth = response.data.netWorth
            userProfile.wallet = response.data.wallet
          }
        case Failure(e) =>
          toaster.error("Error loading user profile");
      }
    }
  }

  /////////////////////////////////////////////////////////////////////
  //          NetWorth Functions
  /////////////////////////////////////////////////////////////////////

  def deduct(amount: Double): Unit = userProfile.wallet = userProfile.wallet.map(_ - amount)

  /////////////////////////////////////////////////////////////////////
  //          Symbols - Favorites, Recent, etc.
  /////////////////////////////////////////////////////////////////////

  def addFavoriteSymbol(symbol: String): Unit = userProfile._id foreach (id => profileService.addFavoriteSymbol(id, symbol))

  def getFavoriteSymbols: js.UndefOr[js.Array[String]] = userProfile.favoriteSymbols

  def isFavoriteSymbol(symbol: String): Boolean = getFavoriteSymbols.exists(_.contains(symbol))

  def removeFavoriteSymbol(symbol: String): Unit = userProfile._id foreach (id => profileService.removeFavoriteSymbol(id, symbol))

  def addRecentSymbol(symbol: String): Unit = userProfile._id foreach (id => profileService.addRecentSymbol(id, symbol))

  def getRecentSymbols: js.UndefOr[js.Array[String]] = userProfile.recentSymbols

  def isRecentSymbol(symbol: String): Boolean = getRecentSymbols.exists(_.contains(symbol))

  def removeRecentSymbol(symbol: String): Unit = userProfile._id foreach (id => profileService.removeRecentSymbol(id, symbol))

  def getMostRecentSymbol: String = getRecentSymbols.toOption.flatMap(_.lastOption) getOrElse "AAPL"

  /////////////////////////////////////////////////////////////////////
  //          Contest Functions
  /////////////////////////////////////////////////////////////////////

  def getContest: js.Object = contest_? getOrElse JS()

  def getContestID: js.UndefOr[String] = contest_?.orUndefined.flatMap(_._id)

  def getContestName: String = contest_?.flatMap(_.name.toOption).orNull

  def getContestStatus: String = contest_?.flatMap(_.status.toOption).orNull

  def loadContestByID(contestId: String)(implicit ec: ExecutionContext): Future[Option[(Contest, Portfolio, String, Option[Participant])]] = {
    Future.sequence {
      userProfile._id map { playerId =>
        console.log(s"Loading contest $contestId (player: $playerId)...")
        for {
          contest <- contestService.getContestByID(contestId).map(_.data)
          portfolio <- portfolioService.getPortfolioByPlayer(contestId, playerId).map(_.data)
          participantOpt = contest.participants.toOption.flatMap(_.find(_.is(playerId)))
        } yield {
          // set the variables
          contest_? = Option(contest)
          participant_? = participantOpt
          portfolio_? = Option(portfolio)

          // broadcast the event
          $rootScope.emitContestSelected(contest)

          // return the tuple
          (contest, portfolio, playerId, participantOpt)
        }
      } toList
    } map (_.headOption)
  }

  def updateRankings(contest: Contest): Contest = {
    if (contest.leader.nonAssigned || contest.player.nonAssigned) {
      contest.leader = contest.participants.flatMap(_.find(_.rank.contains("1st")).orUndefined)
      contest.player = contest.participants.flatMap(_.find(_._id ?== userProfile._id).orUndefined)
    }
    contest
  }

  def updatePortfolio(portfolio: Portfolio): Unit = {
    console.log(s"portfolio = ${angular.toJson(portfolio)}")
    portfolio_? = Option(portfolio)
  }

  def setContest(contest: Contest): Unit = {
    contest_? = Option(contest)

    // if the player is defined ...
    for {
      contestId <- contest._id
      playerId <- userProfile._id
    } {
      // attempt to find the participant
      participant_? = for {
        playerId <- userProfile._id.toOption
        participant <- contest.participants.toOption.flatMap(_.find(_.is(playerId)))
      } yield participant

      // lookup the portfolio
      portfolioService.getPortfolioByPlayer(contestId, playerId).map(_.data) foreach { portfolio =>
        portfolio_? = Option(portfolio)
      }
    }

    console.log(s"setContest: contest = %s, participant = %s, portfolio = %s",
      angular.toJson(contest), angular.toJson(participant_?.orNull), angular.toJson(portfolio_?.orNull))
  }

  /**
    * Returns the combined total funds for both the cash and margin accounts
    */
  def getCompleteFundsAvailable: Option[Double] = {
    for {
      cashAccount <- cashAccount_?
      cashFunds = cashAccount.funds getOrElse 0.00d
      marginAccount <- marginAccount_?
      marginFunds = marginAccount.funds getOrElse 0.00d
    } yield cashFunds + marginFunds
  }

  def getFundsAvailable: Double = cashAccount_?.orUndefined.flatMap(_.funds) getOrElse 0.00d

  def deductFundsAvailable(amount: Double): Unit = {
    portfolio_? foreach { portfolio =>
      console.log(s"Deducting funds: $amount from ${portfolio.cashAccount.flatMap(_.funds)}")
      portfolio.cashAccount.foreach(acct => acct.funds = acct.funds.map(_ - amount))
      // TODO rethink this
    }
    ()
  }

  def getMessages: js.Array[ChatMessage] = {
    contest_?.orUndefined.flatMap(_.messages.flat) getOrElse emptyArray[ChatMessage]
  }

  def setMessages(messages: js.Array[ChatMessage]): Unit = contest_?.foreach(_.messages = messages)

  def getMyAwards: js.Array[String] = userProfile.awards getOrElse emptyArray

  def getOrders: js.Array[Order] = portfolio_?.orUndefined.flatMap(_.orders) getOrElse emptyArray

  def getClosedOrders: js.Array[Order] = portfolio_?.orUndefined.flatMap(_.closedOrders) getOrElse emptyArray

  def getPerformance: js.Array[Performance] = portfolio_?.orUndefined.flatMap(_.performance) getOrElse emptyArray

  def getPerks: js.Array[String] = portfolio_?.orUndefined.flatMap(_.perks) getOrElse emptyArray

  def hasPerk(perkCode: String): Boolean = getPerks.contains(perkCode)

  def getPositions: js.Array[Position] = portfolio_?.orUndefined.flatMap(_.positions) getOrElse emptyArray

  def resetContest(): Unit = {
    contest_? = None
    participant_? = None
    portfolio_? = None
  }

  ////////////////////////////////////////////////////////////
  //          Social Network Methods
  ////////////////////////////////////////////////////////////

  def doFacebookLogin(): Unit = {
    // perform the login
    console.log(s"Performing Facebook login...")
    facebook.login() onComplete {
      case Success(response) =>
        console.log("Facebook login successful.")
        doPostLoginUpdates(facebookID = response.authResponse.userID, userInitiated = false)
      case Failure(e) =>
        console.error(s"Facebook: ${e.displayMessage}")
    }
  }

  def doPostLoginUpdates(facebookID: String, userInitiated: Boolean): Unit = {
    console.log(s"facebookID = $facebookID, userInitiated = $userInitiated")

    // capture the Facebook user ID
    setFacebookID(facebookID)

    val outcome = for {
    // load the user"s Facebook profile
      fbProfile <- {
        console.log(s"Retrieving Facebook profile for FBID $facebookID...")
        facebook.getUserProfile
      }

      // load the user"s Facebook friends
      fbFriends <- {
        console.log(s"Loading Facebook taggable friends for FBID $facebookID...")
        facebook.getTaggableFriends
      }

      // load the user"s ShockTrade profile
      profile <- {
        console.log(s"Retrieving ShockTrade profile for FBID $facebookID...")
        profileService.getProfileByFacebookID(facebookID).map(_.data)
      }

      // retrieve the updated network
      netWorth <- profileService.getNetWorth(profile._id.orNull).map(_.data)

    } yield (fbProfile, fbFriends, profile, netWorth)

    outcome onComplete {
      case Success((fbProfile, fbFriends, profile, netWorth)) =>
        console.log("ShockTrade user profile, Facebook profile, and friends loaded...")
        $rootScope.$apply(() => {
          profile.netWorth = netWorth.value
          setUserProfile(profile, fbProfile)
          this.fbFriends_? = fbFriends
        })
      case Failure(e) =>
        toaster.error(s"ShockTrade Profile retrieval error - ${e.getMessage}")
    }
  }

  def isFacebookConnected: Boolean = facebookID.nonEmpty

  ////////////////////////////////////////////////////////////
  //          Notification Methods
  ////////////////////////////////////////////////////////////

  def addNotification(message: String): js.Array[String] = {
    while (notifications.push(message) > 20) notifications.shift()
    notifications
  }

  def getNotifications: js.Array[String] = notifications

  def hasNotifications: Boolean = notifications.nonEmpty

  ////////////////////////////////////////////////////////////
  //          Participant Methods
  ////////////////////////////////////////////////////////////

  def findPlayerByID(contest: Contest, playerId: String): Option[Participant] = {
    if (isDefined(contest) && isDefined(contest.participants))
      contest.participants.toOption.flatMap(_.find(_._id.contains(playerId)))
    else
      None
  }

  ////////////////////////////////////////////////////////////
  //          Private Methods
  ////////////////////////////////////////////////////////////

  /**
    * Creates a default 'Spectator' user profile
    * @return {{name: string, country: string, level: number, lastSymbol: string, friends: Array, filters: *[]}}
    */
  private def createSpectatorProfile() = {
    notifications.removeAll()
    facebookID = js.undefined
    fbFriends_? = emptyArray[TaggableFriend]
    fbProfile_? = js.undefined
    resetContest()

    new UserProfile(
      name = "Spectator",
      country = "us",
      lastSymbol = "AAPL"
    )
  }

  private[client] def cashAccount_? = portfolio_?.flatMap(_.cashAccount.toOption)

  private[client] def marginAccount_? = portfolio_?.flatMap(_.marginAccount.toOption)

  /////////////////////////////////////////////////////////////////////////////
  //			Events
  /////////////////////////////////////////////////////////////////////////////

  $rootScope.onUserProfileUpdated { (_, profile) =>
    console.log(s"User Profile for ${profile.name} updated")
    for {
      userId <- userProfile._id
      otherId <- profile._id
    } if (userId == otherId) {
      userProfile.wallet = profile.wallet
      toaster.success("Your Wallet", s"<ul><li>Your wallet now has $$${profile.wallet}</li></ul>", 5.seconds/*, "trustedHtml"*/)
    }
  }

  private def info(contest: Contest, message: String) = console.log(s"${contest.name}: $message")

}
