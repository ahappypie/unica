package io.github.ahappypie.unica

import java.util.logging.Logger

import io.grpc.{Server, ServerBuilder}
import io.github.ahappypie.unica.grpc.unica.{UnicaGrpc, UnicaRequest, UnicaResponse}

import scala.concurrent.{ExecutionContext, Future}

object UnicaServer {
  private val logger = Logger.getLogger(classOf[UnicaServer].getName)

  def main(args: Array[String]): Unit = {
    val deploymentId = sys.env.getOrElse("DEPLOYMENT_ID", "0").toLong
    val server = new UnicaServer(ExecutionContext.global, deploymentId)
    server.start()
    server.blockUntilShutdown()
  }

  private val port = sys.env.getOrElse("GRPC_PORT", "50001").toInt
}

class UnicaServer(ec: ExecutionContext, did: Long) { self =>
  private[this] var server: Server = null

  val epoch: Long = 1543622400000L

  private val deploymentIdBits = 10L
  private val maxDeploymentId = -1L ^ (-1L << deploymentIdBits)
  private val sequenceBits = 12L

  private val deploymentIdShift = sequenceBits
  private val timestampShift = sequenceBits + deploymentIdBits
  private val sequenceMask = -1L ^ (-1L << sequenceBits)

  private var lastTimestamp = -1L
  private var sequence = 0L

  private def start(): Unit = {
    if(did > maxDeploymentId || did < 0) {
      throw new IllegalArgumentException("deployment id can't be greater than %d or less than 0".format(maxDeploymentId))
    }

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
    override def getID(req: UnicaRequest) = {
      Future.successful(UnicaResponse(generate()))
    }

    def generate(): Long = synchronized {
      var timestamp = System.currentTimeMillis()

      if(timestamp < lastTimestamp) {
        System.err.println("clock is moving backwards. Rejecting requests until %d", lastTimestamp)
        throw new Exception("clock is moving backwards. Rejecting requests for %d milliseconds".format(lastTimestamp - timestamp))
      }

      if(lastTimestamp == timestamp) {
        sequence = (sequence + 1) & sequenceMask
        if(sequence == 0) {
          timestamp = holdUntil(lastTimestamp)
        }
      } else {
        sequence = 0
      }

      lastTimestamp = timestamp

      ((timestamp - epoch) << timestampShift) |
        (did << deploymentIdShift) |
        sequence
    }

    private def holdUntil(ts: Long): Long = {
      var timestamp = System.currentTimeMillis()
      while(timestamp <= ts) {
        timestamp = System.currentTimeMillis()
      }
      timestamp
    }
  }

}