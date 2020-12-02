package olx.scrap

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.headers.{Cookie, HttpCookie, HttpCookiePair}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Concat, Flow, Source}
import olx.{Config, Order}
import org.slf4j.LoggerFactory
import reactivemongo.api.AsyncDriver
import reactivemongo.api.bson.collection.BSONCollection
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

object Scrapper {
  private val httpClient: HttpProxyClient = HttpProxyClient()
  private val log = LoggerFactory.getLogger(this.getClass)

  def createDownloadStream(order: Order, mongoDriver: AsyncDriver)()(implicit
    globalState: ActorSystem[StateActor.Request],
    ec: ExecutionContext
  ): Future[Source[JsValue, _]] =
    Mongo.getMongoCollection(order, mongoDriver).map {
      mongoCollection: BSONCollection =>
        log.info(s"Starting job for {} ...", order)
        val advertisements: Source[JsValue, NotUsed] =
          Source
            .unfoldAsync(order.olxUrl)(downloadAdvertisementsHref)
            .via(Mongo.filterSavedUrls(order, mongoCollection))
            .take(order.max)
            .via(downloadAdvertisements)
            .via{
              if(order.parsePhones.getOrElse(false))
                downloadPhones
              else
                Flow[(Map[String, String], Cookie, String)].map(_._1)
            }
            .mapAsyncUnordered(1)(Mongo.saveToMongo(order, mongoCollection))
            .map(_.removed("html"))
            .map(_.toJson)
        Source.combine(advertisements, Source.single(order.toJson))(Concat(_))
    }

  private def downloadAdvertisementsHref(uri: String)(implicit
    globalState: ActorSystem[StateActor.Request],
    ec: ExecutionContext
  ): Future[Option[(String, List[String])]] = {
    if(uri.nonEmpty) // Parser.parseHrefs returns empty uri when no next page is found
      httpClient
        .send(HttpRequest(uri = uri), Config.retries)
        .map(Parser.parseHrefs(uri))
    else Future.successful(None)
  }

  private def downloadAdvertisements(implicit
    globalState: ActorSystem[StateActor.Request],
    ec: ExecutionContext
  ): Flow[String, (Map[String, String], Cookie, String), NotUsed] = {
    Flow[String]
      .throttle(Config.throttleElements, Config.throttleInterval)
      .mapAsyncUnordered(Config.numberOfDownloadThreads) { uri =>
        for {
          response <-
            httpClient.sendRequest(HttpRequest(uri = uri), Config.retries)
          responseBody <- Unmarshal(response.entity).to[String]
        } yield {
          val phoneToken: String = Parser.getPhoneToken(responseBody, uri)
          val cookies: Cookie = Cookie(getCookies(response).map {
            case (name @ "pt", _) => HttpCookiePair(name, phoneToken)
            case (name, value) => HttpCookiePair(name, value)
          })
          val data: Map[String, String] = Parser.parseAd(responseBody, uri)
          (data, cookies, phoneToken)
        }
      }
  } //.withAttributes(ActorAttributes.supervisionStrategy(_ => Supervision.Resume))

  private def getCookies(httpResponse: HttpResponse): Seq[(String, String)] =
    httpResponse.headers.collect {
      case c: akka.http.scaladsl.model.headers.`Set-Cookie` => c.cookie.name() -> c.cookie.value()
    }

  private def downloadPhones(implicit
    globalState: ActorSystem[StateActor.Request],
    ec: ExecutionContext
  ): Flow[(Map[String, String], Cookie, String), Map[String, String], NotUsed] =
    Flow[(Map[String, String], Cookie, String)]
      .throttle(Config.throttleElements, Config.throttleInterval)
      .mapAsyncUnordered(Config.numberOfDownloadThreads) {
        case (data, cookie, phoneToken) =>
          val usrid = data("usrid")
          val phonesUri =
            s"https://www.olx.ua/ajax/misc/contact/phone/$usrid/?pt=$phoneToken"
          val phoneReq = HttpRequest(uri = phonesUri).withHeaders(cookie)
          log.debug(
            s"Downloading phones for `${data("url")}` from `$phonesUri` ..."
          )
          (httpClient
            .send(phoneReq, Config.retries)
            .map { response: String =>
              val phones: String = Parser.parsePhones(response)
              data.updated("phones", phones)
            })
            .recover {
              case e =>
                log.error(
                  "Failed to get phones for {}: {}!",
                  data("url"),
                  e.getMessage
                )
                data
            }
      } //.withAttributes(ActorAttributes.supervisionStrategy(_ => Supervision.Resume))

}
