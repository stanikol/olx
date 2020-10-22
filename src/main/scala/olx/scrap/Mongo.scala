package olx.scrap

import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import olx.{Config, Order}
import org.slf4j.LoggerFactory
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{BSONDocument, BSONString}
import reactivemongo.api.{AsyncDriver, MongoConnection}

import scala.concurrent.{ExecutionContext, Future}

object Mongo {

  private val log = LoggerFactory.getLogger(this.getClass)

  def saveToMongo(order: Order, collection: BSONCollection)(
      data: Map[String, String]
  )(implicit
      executionContext: ExecutionContext,
      stateActor: ActorRef[StateActor.Request]
  ): Future[Map[String, String]] = {
    val dataWithId = data.updated("_id", data("url").split("#").head)
    val bson: BSONDocument = BSONDocument(
      dataWithId.view.mapValues(BSONString.apply).toMap
    )
    val Config.MongoDb(mongoUrl, db) = Config.mongo
    collection.insert.one(bson).map { status =>
      log.info(
        s"Ad [{}] is written to {} {} {} with status: {}",
        dataWithId("url"),
        mongoUrl,
        db,
        collection.name,
        status
      )
      stateActor ! StateActor.DeleteUrl(order, dataWithId("_id"))
      dataWithId
    }
  }

  def getMongoCollection(order: Order, mongoDriver: AsyncDriver)(implicit
      executionContext: ExecutionContext
  ): Future[BSONCollection] = {
    for {
      mongoUri <- MongoConnection.fromString(Config.mongo.url)
      connection <- mongoDriver.connect(mongoUri)
      database <- connection.database(Config.mongo.database)
    } yield database.collection(order.collection)
  }

  def filterSavedUrls(order: Order, collection: BSONCollection)(implicit
      globalStage: ActorSystem[StateActor.Request],
      executionContext: ExecutionContext
  ): Flow[List[String], String, _] =
    Flow[List[String]]
      .mapAsyncUnordered(1)(Mongo.checkIfWasSavedToDb(order, collection))
      .mapConcat(identity)
      .filterNot(_._2)
      .map(_._1)

  private def checkIfWasSavedToDb(order: Order, collection: BSONCollection)(
      urls: List[String]
  )(implicit
      globalStage: ActorSystem[StateActor.Request],
      executionContext: ExecutionContext
  ): Future[List[(String, Boolean)]] = {
    val q = BSONDocument("url" -> BSONDocument("$in" -> urls))
    val checkedUrls: Future[List[(String, Boolean)]] = collection
      .find(q)
      .cursor[BSONDocument]()
      .collect[List](urls.length)
      .map(_.map(_.getAsOpt[String]("_id")).collect { case Some(url) => url })
      //        .map(_.map(_.getAsOpt[String]("_id").get))
      .map { result: List[String] =>
        if (result.isEmpty) urls.map(_ -> false)
        else {
          log.info(
            "{} urls nave been already saved to mongo [{}].",
            result.length,
            order
          )
          urls.map(url => url -> result.contains(url))
        }
      }
    checkedUrls.flatMap { urls: List[(String, Boolean)] =>
      implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
      Future.sequence(urls.map {
        case (url, false) =>
          globalStage
            .ask[Boolean](StateActor.CheckAndStoreUrl(_, order, url))
            .map(isSaved => url -> isSaved)
        case u @ (_, true) => Future.successful(u)
      })
    }
  }
}
