package io.github.ahappypie.unica

import java.util.logging.Logger

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout

import io.grpc.{Server, ServerBuilder}
import io.github.ahappypie.unica.grpc.unica.{UnicaGrpc, UnicaRequest, UnicaResponse}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object UnicaServer {
  private val logger = Logger.getLogger(classOf[UnicaServer].getName)

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("unica-system")
    val deploymentId = 0L
    val unicaSupervisor = actorSystem.actorOf(UnicaSupervisor.props(deploymentId), "unica-supervisor")
    val server = new UnicaServer(ExecutionContext.global, unicaSupervisor)
    server.start()
    server.blockUntilShutdown()
  }

  private val port = sys.env.getOrElse("GRPC_PORT", "50001").toInt
}

class UnicaServer(ec: ExecutionContext, us: ActorRef) { self =>
  private[this] var server: Server = null

  private def start(): Unit = {
    server = ServerBuilder.forPort(UnicaServer.port).addService(UnicaGrpc.bindService(new UnicaImpl, ec)).build.start
    UnicaServer.logger.info("Server started, listening on " + UnicaServer.port)
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class UnicaImpl extends UnicaGrpc.Unica {
    implicit val timeout = Timeout(5 seconds)

    override def getID(req: UnicaRequest) = {
      us.ask(req).mapTo[UnicaResponse]
    }
  }

}