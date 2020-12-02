package olx


import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

final case class Order(olxUrl: String, max: Int, collection: String, parsePhones: Option[Boolean])

object Order {
  implicit val orderFormat: RootJsonFormat[Order] = jsonFormat4(Order.apply)
}
