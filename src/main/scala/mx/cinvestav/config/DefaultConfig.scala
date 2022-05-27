package mx.cinvestav.config

import cats.implicits._
import cats.effect._
import io.circe.Json
import mx.cinvestav.Declarations.NodeContext
import mx.cinvestav.commons.docker.Image
import mx.cinvestav.commons.types.{NodeReplicationSchema, SystemReplicationResponse}
import org.http4s._
import org.http4s.{Request, Uri}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._

trait NodeInfo {
    def protocol: String
    def ip: String
    def hostname: String
    def port: Int
    def apiVersion: String
}

case class DataReplicationSystem(hostname:String, port:Int, apiVersion:Int){
  def url:String = s"http://$hostname:$port"
  def apiUrl:String = s"${this.url}/api/v$apiVersion"
  def reset()(implicit ctx:NodeContext) = for {
    _   <- IO.unit
    req =  Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"$apiUrl/reset")
    )
    s   <- ctx.client.status(req)
    _   <- ctx.logger.info(s"RESET_STATUS $s")
  } yield ()
}
case class SystemReplication(protocol:String="http", ip:String="127.0.0.1", hostname:String="localhost",
                              port:Int=1025,
                              apiVersion:String="v2"
                            ) extends NodeInfo{

  def url:String = s"$protocol://$hostname:$port"
  def apiUrl:String = s"${this.url}/api/$apiVersion"
  def createNodeStr:String = s"${this.apiUrl}/create/cache-node"
  def createNodeUri:Uri = Uri.unsafeFromString(s"http://${hostname}:${port}/api/v2/nodes")
//
//  def createNode() = {

//  }

  def createNode(nrs:NodeReplicationSchema)(implicit ctx:NodeContext) = {
    val json =  nrs.asJson
//      Json.obj(
//      "whath"
//      "cacheSize" -> cacheSize.asJson,
//      "policy" -> policy.asJson,
//      "networkName" -> networkName.asJson,
//      "environments" -> environments.asJson,
//      "image" -> image.asJson
//    )
    val req = Request[IO](
      method = Method.POST,
      uri    = this.createNodeUri
//      uri    = Uri.unsafeFromString(s"http://$hostname:$port/api/v$apiVersion/nodes")
    ).withEntity(json)
    //      .withEntity(json)
    for {
      _      <- ctx.logger.debug(s"CREATE_NODE_URI ${this.createNodeUri}")
      status <- ctx.client.expect[SystemReplicationResponse](req)
      _      <- ctx.logger.debug(s"CREATE_NODE_STATUS $status")
    } yield status

  }
  def launchNode()(implicit ctx:NodeContext) = for {
    _                       <- IO.unit
    systemReplicationSignal = ctx.systemReplicationSignal
    currentSignalValue      <- systemReplicationSignal.get
    _                       <- ctx.logger.debug(s"UPLOAD_SIGNAL_VALUE $currentSignalValue")
    nrs                     = NodeReplicationSchema.empty(id = "",metadata = Map.empty[String,String])
    res                     <- if(ctx.config.systemReplicationEnabled && !currentSignalValue) for {
        _ <- ctx.systemReplicationSignal.set(true)
        x <- createNode(nrs = nrs).map(_.some)
        _ <- ctx.systemReplicationSignal.set(false)
      } yield x
    else IO.pure(None)
  }  yield res

  def reset()(implicit ctx:NodeContext) = for {
    _   <- IO.unit
    req =  Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"$apiUrl/reset")
    )
    s   <- ctx.client.status(req)
    _   <- ctx.logger.info(s"RESET_STATUS $s")
  } yield ()
}

case class DefaultConfig(
                          port:Int = 0,
                          nodeId:String = "",
                          host:String ="",
                          systemReplication:SystemReplication = SystemReplication(),
                          uploadLoadBalancer:String = "SORTING_UF",
                          downloadLoadBalancer:String = "LEAST_HIT",
                          returnHostname:Boolean = false,
                          cloudEnabled:Boolean = false,
                          hasNextPool:Boolean = false,
                          apiVersion:Int = 2,
                          daemonDelayMs:Int = 1000,
                          usePublicPort:Boolean = false,
                          maxConnections:Int = 1000,
                          bufferSize:Int = 43000,
                          responseHeaderTimeoutMs:Long = 10000,
                          elasticity:Boolean = false,
                          availableResources:Int = 5,
                          replicationFactor:Int = 3,
                          systemReplicationEnabled:Boolean = false,
                          replicationTechnique:String = "ACTIVE",
                          replicationTransferType:String = "PUSH",
                          nSemaphore:Int = 1,
                          defaultImpactFactor:Double = 0.0,
                          elasticityTime:String = "DEFERRED"
                        )
