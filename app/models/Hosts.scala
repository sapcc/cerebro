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
import java.net.ConnectException

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}

import java.net.ConnectException

@ImplementedBy(classOf[HostsImpl])
trait Hosts {

  def getHostNames(): Seq[String]

  def getHost(name: String): Option[Host]

}

@Singleton
class HostsImpl @Inject()(config: Configuration, client: WSClient)(implicit system: ActorSystem, mat: Materializer) extends Hosts {

  
  def fetchWithRetry(request: WSRequest, retries: Int = 20, delay: FiniteDuration = 10.seconds): Future[WSResponse] = {
    request.execute().recoverWith {
      case ex if retries > 0 =>
        println(s"Request ${request} failed: ${ex}, ${ex.getMessage}. Retrying in ${delay.toSeconds} seconds... ($retries retries left)")
        akka.pattern.after(delay, system.scheduler)(fetchWithRetry(request, retries - 1, delay))
    }
  }

  def execute[T](uri: String,
                 method: String,
                 body: Option[String] = None,
                 target: ElasticServer,
                 headers: Seq[(String, String)] = Seq()) : Future[Option[elastic.ElasticResponse]] = {
    val authentication = target.host.authentication
    val url = s"${target.host.name.replaceAll("/+$", "")}$uri"

    val mergedHeaders = headers ++ target.headers

    val request =
      authentication.foldLeft(client.url(url).withMethod(method).withHttpHeaders(mergedHeaders: _*)) {
      case (request, auth) =>
        request.withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
    }
    Console.println(s"Executing req: $request, $authentication")
    fetchWithRetry(body.fold(request)(request.withBody((_))))
    .map {case response => Some(ElasticResponse(response)) }
    .recover {case ex => None} 
  }

  def clusterHealth(target: ElasticServer): Future[Option[ElasticResponse]] = {
    val path = "/_cluster/health"
    execute(path, "GET", None, target)
  }


  val hosts: Map[String, Host] = Try(config.underlying.getConfigList("hosts").asScala.map(Configuration(_))) match {
    case scala.util.Success(hostsConf) => hostsConf.map { hostConf =>
      val host = hostConf.getOptional[String]("host").get
      val name = hostConf.getOptional[String]("name").getOrElse(host)
      val headersWhitelist = hostConf.getOptional[Seq[String]](path = "headers-whitelist")  .getOrElse(Seq.empty[String])
      
      val res = for (name <- List("auth", "auth2")) {
        val username = hostConf.getOptional[String](s"$name.username").getOrElse("")
        val password = hostConf.getOptional[String](s"$name.password").getOrElse("")
        val creds = Host(host, Some(ESAuth(username, password)), headersWhitelist)
        
        val resp = clusterHealth(ElasticServer(creds, List())).map {
          case Some(response) => (response) match {
            case elastic.Success(status, health) => Some(creds)
            case elastic.Error(status, error) => None
        }
        case None => None
      }

      Console.println(s"resp $resp")
      name -> creds
    }

    Console.println("result: " + res)
    val test = 1
    (test) match {
      case 1 => name -> Host(host, None, headersWhitelist)
      case 2 => name -> Host(host, None, headersWhitelist)
    }
    }.toMap
    case Failure(_) => Map()
  }

  def getHostNames() = hosts.keys.toSeq

  def getHost(name: String) = hosts.get(name)

}
