package models

import javax.inject.Singleton

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import elastic.{HTTPElasticClient, ElasticResponse, Error, Success}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import play.api.libs.ws.{WSAuthScheme, WSClient}

@ImplementedBy(classOf[HostsImpl])
trait Hosts {

  def getHostNames(): Seq[String]

  def getHost(name: String): Option[Host]

}

@Singleton
class HostsImpl @Inject()(config: Configuration, client: HTTPElasticClient) extends Hosts {

  
  // def clusterHealth(target: ElasticServer): Future[ElasticResponse] = {
  //   val path = "/_cluster/health"
  //   execute(path, "GET", None, target)
  // }

  // def execute[T](uri: String,
  //                method: String,
  //                body: Option[String] = None,
  //                target: ElasticServer,
  //                headers: Seq[(String, String)] = Seq()) = {
  //   val authentication = target.host.authentication
  //   val url = s"${target.host.name.replaceAll("/+$", "")}$uri"

  //   val mergedHeaders = headers ++ target.headers

  //   val request =
  //     authentication.foldLeft(client.url(url).withMethod(method).withHttpHeaders(mergedHeaders: _*)) {
  //     case (request, auth) =>
  //       request.withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
  //   }
  //   Console.println(s"Executing req: $request, $authentication")

  //   body.fold(request)(request.withBody((_))).execute().map { response =>
  //     ElasticResponse(response)
  //   }
  // }

  val hosts: Map[String, Host] = Try(config.underlying.getConfigList("hosts").asScala.map(Configuration(_))) match {
    case scala.util.Success(hostsConf) => hostsConf.map { hostConf =>
      val host = hostConf.getOptional[String]("host").get
      val name = hostConf.getOptional[String]("name").getOrElse(host)
      val headersWhitelist = hostConf.getOptional[Seq[String]](path = "headers-whitelist")  .getOrElse(Seq.empty[String])
      
      
      val username = hostConf.getOptional[String]("auth.username").getOrElse("")
      val password = hostConf.getOptional[String]("auth.password").getOrElse("")
      val username2 = hostConf.getOptional[String]("auth2.username").getOrElse("")
      val password2 = hostConf.getOptional[String]("auth2.password").getOrElse("")

      val creds = Host(host, Some(ESAuth(username, password)), headersWhitelist)
      val creds2 = Host(host, Some(ESAuth(username2, password2)), headersWhitelist)      
      
      // val resp = clusterHealth(ElasticServer(creds, List())).map {
      //   case elastic.Success(status, tokens) => creds
      //   case elastic.Error(status, error) => None
      // }
      // val resp2 = clusterHealth(ElasticServer(creds2, List())).map {
      //   case elastic.Success(status, tokens) => creds2
      //   case elastic.Error(status, error) => None
      // }

      // Console.println(s"HOSTImpl $username, $password")
      // Console.println(s"resp $resp")
      // Console.println(s"resp2 $resp2")


    val resp = client.clusterHealth(ElasticServer(creds, List())).map {
      case elastic.Success(status, health) => Some(creds)
      case elastic.Error(status, error) => None
    }

    val resp2 = client.clusterHealth(ElasticServer(creds2, List())).map {
      case elastic.Success(status, health) => Some(creds)
      case elastic.Error(status, error) => None
    }
    val r = Await.result(resp, 10000.millis)
    val r2 = Await.result(resp2, 10000.millis)

    Console.println(s"resp $resp")
    Console.println(s"resp2 $resp2")
    Console.println(s"r $r")
    Console.println(s"r2 $r2")
    (r, r2) match {
      case (Some(c), _) => name -> c
      case (_, Some(c2)) => name -> c2
      case (None, None) => name -> Host(host, None, headersWhitelist)
    }
    }.toMap
    case Failure(_) => Map()
  }

  def getHostNames() = hosts.keys.toSeq

  def getHost(name: String) = hosts.get(name)

}
