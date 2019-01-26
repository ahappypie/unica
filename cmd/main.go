package main

import (
	"context"
	"log"
	"net"
	"time"

	"google.golang.org/grpc"

	"github.com/ahappypie/unica/pkg/api"

	"google.golang.org/grpc/reflection"
)

const (
	port = ":50001"

	epoch = uint64(1543622400000)

	deploymentIdBits = uint64(10)
	maxDeploymentId  = int64(-1) ^ (int64(-1) << deploymentIdBits)
	sequenceBits     = uint64(12)

	deploymentIdShift = sequenceBits
	timestampShift    = sequenceBits + deploymentIdBits
	sequenceMask      = uint64(int64(-1) ^ (int64(-1) << sequenceBits))

	did = uint64(0)
)

var sequence = uint64(0)
var lastTimestamp = uint64(0)

type Server struct{}

func (s *Server) GetId(ctx context.Context, in *unica.UnicaRequest) (*unica.UnicaResponse, error) {
	//log.Printf("Recevied request")
	id, err := generate()
	return &unica.UnicaResponse{Id: id}, err
}

func main() {
	lis, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("falied to bind: %v", err)
	}
	s := grpc.NewServer()
	unica.RegisterIdServiceServer(s, &Server{})
	reflection.Register(s)
	err2 := s.Serve(lis)
	if err2 != nil {
		log.Fatalf("failed to serve: %v", err2)
	}
}

func generate() (uint64, error) {
	var timestamp = makeTimestamp()

	if timestamp < lastTimestamp {
		log.Printf("clock is moving backwards. Waiting for %d milliseconds", (lastTimestamp - timestamp))
		timestamp = holdUntil(lastTimestamp)
	}

	if lastTimestamp == timestamp {
		sequence = (sequence + 1) & sequenceMask
		if sequence == 0 {
			timestamp = holdUntil(lastTimestamp)
		}
	} else {
		sequence = 0
	}

	lastTimestamp = timestamp

	x := ((timestamp - epoch) << timestampShift) |
		(did << deploymentIdShift) |
		sequence

	return x, nil
}

func holdUntil(ts uint64) uint64 {
	var timestamp = makeTimestamp()
	for timestamp <= ts {
		timestamp = makeTimestamp()
	}
	return timestamp
}

func makeTimestamp() uint64 {
	return uint64(time.Now().UnixNano() / (int64(time.Millisecond) / int64(time.Nanosecond)))
}
