package example.util

import example.model.Operation

object Utils:
	def executeOperation(operation: Operation, left: Double, right: Double): Either[String, Double] =
		operation match
			case Operation.Plus => Right(left + right)
			case Operation.Minus => Right(left - right)
			case Operation.Times => Right(left * right)
			case Operation.Divide =>
				if (right == 0) Left("No division by zero!")
				else Right(left / right)

	import scalajs.js.annotation.*
	import example.model.Value
	def addCommasNonDecimal(numberString: String): String =
		numberString
		  .reverse
		  .grouped(3)
		  .toList
		  .reverse
		  .map(_.reverse)
		  .mkString(",")

	/**
	 * Adds commas to a string-encoded number as appropriate. Detects decimals.
	 *
	 * @param numberString number encoded in a string
	 * @returns {string} A version of strin with commas added as appropriate
	 */
	@JSExportTopLevel("formatNumberString", "utils")
	def formatNumberString(numberString: String): String = numberString match {
		case Value.NonDecimal() => addCommasNonDecimal(numberString)
		case Value.Decimal(nonDecimal, decimal) =>
			val nonDecimalWithCommas = addCommasNonDecimal(nonDecimal)
			s"$nonDecimalWithCommas.$decimal"
		case "" => "0"
		case _ => throw new RuntimeException(s"Invalid number string: $numberString")
	}

