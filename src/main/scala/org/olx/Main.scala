package org.olx

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  val run: IO[Unit] = OlxServer.run
