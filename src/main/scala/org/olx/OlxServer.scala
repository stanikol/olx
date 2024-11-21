package org.olx

import cats.*
import cats.effect.*
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.io.net.Network
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import org.olx.parser.{DownloadAds, Search, WD}
import org.openqa.selenium.remote.RemoteWebDriver

import scala.io.Source

type AppState = List[AppState.Job]

object AppState {
  def empty: AppState = List.empty[Job]

  case class Job(fiberIO: FiberIO[Unit], search: Search)
}

object OlxServer extends DownloadAds:

  def run: IO[Unit] = for {
    appState <- AtomicCell[IO].of(AppState.empty)
    _ <- WD.makeWebDriver(None).use { webDriver =>
      val routes = olxRoutes(webDriver, appState)
      val httpApp = Logger.httpApp[IO](true, true)(routes.orNotFound)
      openOlx(webDriver) >>
        EmberServerBuilder.default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(httpApp)
          .build
          .useForever
    }
  } yield ()

  private def olxRoutes(webDriver: RemoteWebDriver, appState: AtomicCell[IO, AppState]): HttpRoutes[IO] =

    def startDownloadJob(s: Search): IO[Unit] =
      def zeroAppState(s: Search): IO[Unit] = for {
        jobs: AppState <- appState.get
        (found: AppState, rest: AppState) = jobs.partition(_.search == s)
        () <- IO(info(s"zeroAppState(${s.url}): found ${found.length} jobs."))
        () <- appState.set(rest)
      } yield ()

      for {f: FiberIO[Unit] <- (downloadAndSaveAds(s).handleErrorWith { e =>
                                    IO(error(s"Error when running $s: ${e.getMessage}")) >>
                                      zeroAppState(s) >> IO.raiseError(e)
                                  } >> zeroAppState(s)).start
           _ <- IO(info(s"[OK] Finished downloading of $s"))
           _ <- appState.update(jobs => AppState.Job(f, s) :: jobs)
           } yield ()
    end startDownloadJob

    val dsl = new Http4sDsl[IO] {}
    import dsl.*
    HttpRoutes.of[IO] {
      case request@GET -> Root / "olx" =>
        getHtmlFromResource("/start.html")
      case request@GET -> Root / "olx" / "db" =>
        getHtmlFromResource("/db.html")

      case request@GET -> Root / "olx" / "run" =>
        runHtml(appState)

      case request@POST -> Root / "olx" / "run" =>
        (for {
          urlForm: UrlForm <- request.as[UrlForm]
          s: Search <- IO.fromOption(Search.fromUrlForm(urlForm))(new Exception("Invalid form data"))
          search: Search <-
            if (s.url.isEmpty || s.url.exists(_.isEmpty))
              IO(s.copy(url = Some(webDriver.getCurrentUrl)))
                .handleErrorWith(e =>
                  IO(error(s"Error getting search URL from webdriver: ${e.getMessage}")) >>
                    IO.raiseError(e)
                )
            else IO.pure(s)
          _ <- startDownloadJob(search)
          response <- runHtml(appState)
        } yield response).handleErrorWith(e => InternalServerError(e.getMessage))

      case request@POST -> Root / "olx" / "stop" =>
        for {
          _ <- IO(warn("Canceling active downloads ..."))
          jobs <- appState.get
          _ <- jobs.traverse_(_.fiberIO.cancel)
          _ <- appState.set(AppState.empty)
          _ <- IO(warn("Canceling active downloads: [OK]"))
          r <- getHtmlFromResource("/start.html")
        } yield r.withHeaders(Headers(Location(uri"/olx")))
    }

  private def getHtmlFromResource(filename: String): IO[Response[IO]] =
    StaticFile.fromResource(filename, Option.empty[Request[IO]]).getOrElseF(NotFound())

  private def runHtml(appState: AtomicCell[IO, AppState]): IO[Response[IO]] = for {
    jobs: AppState <- appState.get
    () <- IO(info(s"Now running ${jobs.length} jobs"))
    template: String <- IO(Source.fromResource("run.html").mkString)
    jobsHtml: String = jobs.map { case AppState.Job(_, s: Search) =>
      s"<tr><td>${s.name}</td><td>${s.url.getOrElse("")}</td><td>${s.downloadAdsCount}</td><td>${s.time}</td></tr>"
    }.mkString
    html: fs2.Stream[IO, Byte] = fs2.Stream.emits(template.replace("[[JOBS]]", jobsHtml).getBytes).covary[IO]
    response <- Ok(html)
  } yield response

end OlxServer
