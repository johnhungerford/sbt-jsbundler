package example.test

import scalajs.js
import scalajs.js.annotation.*

// Need to bundle sizzle into test script
@js.native
@JSImport("sizzle", JSImport.Default)
object Sizzle extends js.Object

trait InitSizzle:
	// Injects sizzle into global namespace, which is where the
	// test-state library imports it from
	js.Dynamic.global.Sizzle = Sizzle

object TestStateConfig
  extends InitSizzle
	with teststate.Exports
	with teststate.ExtScalaJsReact
	with teststate.domzipper.sizzle.Exports
