package io.github.ahappypie.unica

import java.util.logging.Logger

import io.grpc.{Server, ServerBuilder}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

object UnicaServer {
  private val logger = Logger.getLogger(classOf[UnicaServer].getName)

  def main(args: Array[String]): Unit = {
    val depEnv = sys.env.getOrElse("DEPLOYMENT_ID", "0")
    val deploymentId = depEnv.substring(depEnv.length()-1).toLong
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

    server = ServerBuilder.forPort(UnicaServer.port).addService(IdServiceGrpc.bindService(new UnicaImpl, ec)).build.start
    UnicaServer.logger.info("Server started, listening on " + UnicaServer.port)
    UnicaServer.logger.info("Deployment id " + did)
    UnicaServer.logger.info("Available resources:\nCPU: " + Runtime.getRuntime.availableProcessors() + "\nMax Memory: " + Runtime.getRuntime.maxMemory()/(1024L*1024L) + " MB")
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

  private class UnicaImpl extends IdServiceGrpc.IdService {
    override def getId(req: UnicaRequest) = {
      Future {UnicaResponse(generate())}
    }

    def generate(): Long = synchronized {
      var timestamp = System.currentTimeMillis()

      if(timestamp < lastTimestamp) {
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