package example.test

import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.MountedImpure
import japgolly.scalajs.react.test.ReactTestUtils
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.univeq.UnivEq.*
import teststate.ExtScalaJsReact.DomZipperJs
import teststate.dsl.Dsl
import utest.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}

import example.component.Display
import example.model.*

import TestStateConfig._


object DisplayComponentTest extends TestSuite:
	import scalajs.concurrent.JSExecutionContext.Implicits.queue

	lazy val tests = Tests:

		test("positive value") {
			val value = Value.fromNumber(7654321)
			val operation = None
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
					+> numberText.assert("7,654,321")
					+> operationText.assert("")

			runTest(value, operation, plan).map(_.assert())
		}

		test("negative value") {
			val value = Value.fromNumber(7654321).togglePlusMinus
			val operation = None
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
				  +> numberText.assert("-7,654,321")
				  +> operationText.assert("")

			runTest(value, operation, plan).map(_.assert())
		}

		test("with plus") {
			val value = Value.fromNumber(7654321)
			val operation = Some(Operation.Plus)
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
				  +> numberText.assert("7,654,321")
				  +> operationText.assert("+")

			runTest(value, operation, plan).map(_.assert())
		}

		test("with minus") {
			val value = Value.fromNumber(7654321)
			val operation = Some(Operation.Minus)
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
				  +> numberText.assert("7,654,321")
				  +> operationText.assert("-")

			runTest(value, operation, plan).map(_.assert())
		}

		test("with times") {
			val value = Value.fromNumber(7654321)
			val operation = Some(Operation.Times)
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
				  +> numberText.assert("7,654,321")
				  +> operationText.assert("x")

			runTest(value, operation, plan).map(_.assert())
		}

		test("with divide") {
			val value = Value.fromNumber(7654321)
			val operation = Some(Operation.Divide)
			val plan = Plan.action:
				dsl.action("No action").update(_ => Future.unit)
				  +> numberText.assert("7,654,321")
				  +> operationText.assert("÷")

			runTest(value, operation, plan).map(_.assert())
		}


	// Our dependencies

	final case class ExampleState(value: Int)

	// Test state library's dependencies

	val component = ScalaComponent
	  .builder[Unit]
	  .render(_ => <.div("hello"))
	  .build

	def runTest(value: Value, operation: Option[Operation], plan: dsl.Plan): Future[Report[String]] =
		ReactTestUtils.withRenderedIntoDocumentFuture(
			Display.component(Display.Props(
				value,
				operation,
			))
		) { c =>
			def observe() = new StateTestObs(c.domZipper)
			val test      = plan
			  .withInitialState(())
			  .test(Observer watch observe())

			test.runU()
		}

	val dsl = Dsl.future[Unit, StateTestObs, Unit]

	val operationText = dsl.focus("displayed operation text").value(_.obs.getOperationText())
	val numberText = dsl.focus("displayed number text").value(_.obs.getNumberText())

	class StateTestObs($ : DomZipperJs):
		def getNumberText(): String = $(".component-display-span").innerText

		def getOperationText(): String = $(".operation-view").innerText
