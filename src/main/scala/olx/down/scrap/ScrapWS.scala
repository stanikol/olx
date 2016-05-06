package olx.down.scrap

import olx.Data
import play.api.libs.ws.ahc
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by stanikol on 06.05.16.
  */
trait ScrapWS {

  def fetchAdv(url:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[Data]

  def fetchAdvUrls(url:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[Data]
}
