package org.olx.db

import cats.*
import cats.effect.*
import doobie.*
import doobie.h2.H2Transactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.olx.parser.{Advertisement, Search}
import wvlet.log.LogSupport

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME

trait H2 extends LogSupport:

  val transactor: Resource[IO, H2Transactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](2)
      xa <- H2Transactor.newH2Transactor[IO](
        "jdbc:h2:tcp://h2:1521/olxdb",
        //        "jdbc:h2:/app/db/olx;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE", // connect URL
        //        "jdbc:h2:file:/app/db/olx;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", // connect URL
        "sa", // username
        "", // password
        ce
      )
    } yield xa

  given Get[LocalDateTime] = Get[String].map((t: String) => LocalDateTime.parse(t, ISO_LOCAL_DATE_TIME))

  given Put[LocalDateTime] = Put[String].contramap((t: LocalDateTime) => t.format(ISO_LOCAL_DATE_TIME))

  def saveToDb(h2Transactor: H2Transactor[IO], s: Search)(ads: fs2.Stream[IO, Advertisement]): fs2.Stream[IO, Unit] = for {
    searchId <- fs2.Stream.eval(createTables(h2Transactor).flatMap(_ => insertSearch(h2Transactor)(s)))
    _ <- ads.evalMap(insertAd(h2Transactor, searchId))
  } yield ()

  end saveToDb

  def createTables(h2transactor: H2Transactor[IO]): IO[Unit] = for {
    h2Version <- sql"select value from information_schema.settings where name = 'info.VERSION';".query[String].unique.transact(h2transactor)
    _ <- IO(info(s"Using H2 $h2Version version ..."))
    sql <- IO(scala.io.Source.fromResource("create_tables.sql").mkString(""))
    _ <- Fragment.const(sql).update.run.transact(h2transactor)
    _ <- IO(info(s"[OK] DB tables exist or have just been created."))
  } yield ()

  def insertSearch(h2transactor: H2Transactor[IO])(s: Search): IO[Long] =
    import s.{downloadAdsCount, name, time, url}
    val query = for {
      _ <- sql"INSERT INTO SEARCH (NAME, URL, DOWNLOADCOUNT, START) VALUES ($name, $url, $downloadAdsCount, $time);".update.run
      id <- sql"SELECT LASTVAL();".query[Long].unique
    } yield id
    query.transact(h2transactor)
  end insertSearch

  def insertAd(h2transactor: H2Transactor[IO], searchId: Long)(ad: Advertisement): IO[Unit] =
    import ad.*
    val briefStr = brief.mkString("; ")
    sql"INSERT INTO ADS(SEARCH, HREF, TITLE, DESCRIPTION, PRICE, BRIEF, HTML) VALUES($searchId, $href, $title, $description, $price, $briefStr, $html)"
      .update.run.transact(h2transactor)
      .flatMap(_ => IO(info(s"Ad ${ad.href} was saved to db: [searchId=$searchId]")))
  end insertAd

end H2


