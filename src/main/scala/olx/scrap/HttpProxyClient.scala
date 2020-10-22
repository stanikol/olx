package olx.scrap

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.{
  ClientConnectionSettings,
  ConnectionPoolSettings
}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ClientTransport, Http}
import akka.util.Timeout
import olx.Config
import olx.scrap.StateActor.ProxyUseResult
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait HttpProxyClient {

  private val log = LoggerFactory.getLogger(classOf[HttpProxyClient])

  def sendRequest(request: HttpRequest, proxy: Option[HttpProxy])(
    implicit
    globalState: ActorSystem[StateActor.Request],
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {
    val startTime = System.currentTimeMillis()
    val settings: ConnectionPoolSettings =
      if (proxy.isDefined) {
        val HttpProxy(host, port) = proxy.get
        val httpsProxyTransport = ClientTransport.httpsProxy(
          InetSocketAddress.createUnresolved(host, port)
        )
        ConnectionPoolSettings(globalState)
          .withConnectionSettings(
            ClientConnectionSettings(globalState)
              .withTransport(httpsProxyTransport)
              .withConnectingTimeout(Config.clientConnectingTimeout)
              .withUserAgentHeader(Some(`User-Agent`(olx.Config.userAgent)))
          )
      } else {
        ConnectionPoolSettings(globalState)
          .withConnectionSettings(
            ClientConnectionSettings(globalState)
              .withConnectingTimeout(Config.clientConnectingTimeout)
              .withUserAgentHeader(Some(`User-Agent`(olx.Config.userAgent)))
          )
      }
    log.debug(
      "Trying {} [{}]  proxy={} ...",
      request.method.value,
      request.uri,
      proxy
    )
    Http()
      .singleRequest(request, settings = settings)
      .recoverWith {
        case error =>
          val m =
            s"Error in HttpClient on ${request.method.value} [${request.uri}] proxy=$proxy: ${error.getMessage}!"
          log.error(m)
          Future.failed(new Exception(m))
      }
      .map { response =>
        val endTime = System.currentTimeMillis()
        log.info(
          s"HttpClient got {} response in {} millis for {} [{}]  proxy={}.",
          response.status,
          endTime - startTime,
          request.method.value,
          request.uri,
          proxy
        )
        response
      }
      .flatMap {
        case ok @ HttpResponse(StatusCodes.OK, _, _, _) =>
          Future.successful(ok)
        case HttpResponse(status, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { body: String =>
            val m =
              s"Got unexpected response code $status for ${request.method.value} [${request.uri}] proxy=$proxy: `$body`!"
            log.error(m)
            Future.failed(new Exception(m))
          }
      }

  }

  def sendRequest(request: HttpRequest, retries: Int)(implicit
    globalState: ActorSystem[StateActor.Request],
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {
    implicit val askTimeout: Timeout = Timeout(10, TimeUnit.SECONDS)
    def getProxyIfNeeded =
      if (Config.proxies.isDefined)
        globalState.ask[HttpProxy](StateActor.GetProxy).map(Some.apply)
      else Future.successful(None)

    for {
      proxy <- getProxyIfNeeded
      startTime = System.currentTimeMillis()
      response <- sendRequest(request, proxy)
        .map { response =>
          val responseTime = System.currentTimeMillis() - startTime
          if (proxy.isDefined)
            globalState ! ProxyUseResult(proxy.get, Right(responseTime))
          response
        }
        .recoverWith {
          case error =>
            if (proxy.isDefined)
              globalState ! ProxyUseResult(
                proxy.get,
                Left(error.getLocalizedMessage)
              )
            val retriesLeft = retries - 1
            if (retriesLeft > 0)
              sendRequest(request, retriesLeft)
            else {
              log.error(
                "Failed to send {} [{}] proxy={}: {}!",
                request.method.value,
                request.uri,
                proxy,
                error.getMessage
              )
              Future.failed(error)
            }
        }
    } yield response
  }

  def send(request: HttpRequest, retries: Int = 10)(implicit
    globalState: ActorSystem[StateActor.Request],
    executionContext: ExecutionContext
  ): Future[String] =
    sendRequest(request, retries).flatMap(Unmarshal(_).to[String])
}

object HttpProxyClient {
  def apply(): HttpProxyClient = new HttpProxyClient {}
}
