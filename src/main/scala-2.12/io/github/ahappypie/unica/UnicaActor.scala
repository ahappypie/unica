package io.github.ahappypie.unica

import akka.actor.{Actor, ActorLogging, Props}
import io.github.ahappypie.unica.grpc.unica.{UnicaRequest, UnicaResponse}

object UnicaActor {
  def props(deploymentId: Long): Props = Props(new UnicaActor(deploymentId))
}

class UnicaActor(deploymentId: Long) extends Actor with ActorLogging {

  val epoch: Long = 1543622400000L

  private val deploymentIdBits = 10L
  private val maxDeploymentId = -1L ^ (-1L << deploymentIdBits)
  private val sequenceBits = 12L

  private val deploymentIdShift = sequenceBits
  private val timestampShift = sequenceBits + deploymentIdBits
  private val sequenceMask = -1L ^ (-1L << sequenceBits)

  private var lastTimestamp = -1L
  private var sequence = 0L

  override def preStart(): Unit = {
    super.preStart()

    if(deploymentId > maxDeploymentId || deploymentId < 0) {
      throw new IllegalArgumentException("deployment id can't be greater than %d or less than 0".format(maxDeploymentId))
    }

    log.info("actor starting. timestamp shift %d, deployment id bits %d, sequence bits %d".format(
      timestampShift, deploymentIdBits, sequenceBits))
  }


  override def receive: Receive = {
    case req: UnicaRequest => {
      //log.info("actor generated uid %s".format(uid))
      sender ! UnicaResponse(generate())
      //context.parent ! UnicaResponse(uid)
    }
  }

  def generate(): Long = {
    var timestamp = System.currentTimeMillis()

    if(timestamp < lastTimestamp) {
      log.error("clock is moving backwards. Rejecting requests until %d", lastTimestamp)
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
      (deploymentId << deploymentIdShift) |
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
