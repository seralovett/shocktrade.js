package com.shocktrade.common.models.contest

import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined

/**
  * Represents a Portfolio-like model
  * @author Lawrence Daniels <lawrence.daniels@gmail.com>
  */
@ScalaJSDefined
trait PortfolioLike extends js.Object {

  def playerID: js.UndefOr[String]

  def perks: js.UndefOr[js.Array[String]]

  def cashAccount: js.UndefOr[CashAccount]

  def marginAccount: js.UndefOr[MarginAccount]

  def orders: js.UndefOr[js.Array[_ <: OrderLike]]

  def closedOrders: js.UndefOr[js.Array[_ <: OrderLike]]

  def performance: js.UndefOr[js.Array[_ <: PerformanceLike]]

  def positions: js.UndefOr[js.Array[_ <: PositionLike]]

}